package com.bct.ngtpa.apiservice.application.usecase.message;

import com.bct.ngtpa.apiservice.application.dto.message.EmailMessage;
import com.bct.ngtpa.apiservice.application.dto.message.MessageDeliveryChannel;
import com.bct.ngtpa.apiservice.application.dto.message.MessageDeliveryCommand;
import com.bct.ngtpa.apiservice.application.dto.message.MessageDeliveryResult;
import com.bct.ngtpa.apiservice.application.dto.message.MessageDeliveryStatus;
import com.bct.ngtpa.apiservice.application.dto.message.SenderResult;
import com.bct.ngtpa.apiservice.application.dto.message.SenderStatus;
import com.bct.ngtpa.apiservice.application.dto.message.SmsMessage;
import com.bct.ngtpa.apiservice.application.port.out.EmailSenderPort;
import com.bct.ngtpa.apiservice.application.port.out.MessageDeliveryPort;
import com.bct.ngtpa.apiservice.application.port.out.SmsSenderPort;
import reactor.core.publisher.Mono;

public class MessageDeliveryService implements MessageDeliveryPort {

    private static final String SENDER_RESULT_MISSING = "SENDER_RESULT_MISSING";
    private static final String SENDER_RESULT_MISSING_MESSAGE = "Sender returned no result";

    private final SmsSenderPort smsSenderPort;
    private final EmailSenderPort emailSenderPort;

    public MessageDeliveryService(SmsSenderPort smsSenderPort, EmailSenderPort emailSenderPort) {
        this.smsSenderPort = smsSenderPort;
        this.emailSenderPort = emailSenderPort;
    }

    @Override
    public Mono<MessageDeliveryResult> deliver(MessageDeliveryCommand command) {
        return Mono.defer(() -> {
            validate(command);

            return switch (command.channel()) {
                case SMS -> smsSenderPort.sendSms(toSmsMessage(command))
                        .map(result -> toDeliveryResult(MessageDeliveryChannel.SMS, result));
                case EMAIL -> emailSenderPort.sendEmail(toEmailMessage(command))
                        .map(result -> toDeliveryResult(MessageDeliveryChannel.EMAIL, result));
            };
        });
    }

    private void validate(MessageDeliveryCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("command must not be null");
        }
        if (command.messageType() == null) {
            throw new IllegalArgumentException("messageType must not be null");
        }
        if (command.scenario() == null) {
            throw new IllegalArgumentException("scenario must not be null");
        }
        if (command.channel() == null) {
            throw new IllegalArgumentException("channel must not be null");
        }
        if (isBlank(command.recipient())) {
            throw new IllegalArgumentException("recipient must not be blank");
        }
        if (isBlank(command.body())) {
            throw new IllegalArgumentException("body must not be blank");
        }
        if (command.channel() == MessageDeliveryChannel.EMAIL && isBlank(command.subject())) {
            throw new IllegalArgumentException("subject must not be blank for EMAIL");
        }
    }

    private SmsMessage toSmsMessage(MessageDeliveryCommand command) {
        return new SmsMessage(
                command.recipient(),
                command.body(),
                null,
                command.correlationId());
    }

    private EmailMessage toEmailMessage(MessageDeliveryCommand command) {
        return new EmailMessage(
                command.recipient(),
                command.subject(),
                command.body(),
                null,
                command.correlationId());
    }

    private MessageDeliveryResult toDeliveryResult(MessageDeliveryChannel channel, SenderResult result) {
        if (result == null) {
            return new MessageDeliveryResult(
                    MessageDeliveryStatus.FAILED,
                    channel,
                    null,
                    SENDER_RESULT_MISSING,
                    SENDER_RESULT_MISSING_MESSAGE);
        }

        return new MessageDeliveryResult(
                mapStatus(result.status()),
                channel,
                result.providerMessageId(),
                result.failureCode(),
                result.failureMessage());
    }

    private MessageDeliveryStatus mapStatus(SenderStatus status) {
        return status == SenderStatus.ACCEPTED
                ? MessageDeliveryStatus.ACCEPTED
                : MessageDeliveryStatus.FAILED;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}