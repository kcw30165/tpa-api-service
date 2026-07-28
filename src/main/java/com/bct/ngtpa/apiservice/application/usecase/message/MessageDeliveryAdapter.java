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
import com.bct.ngtpa.apiservice.application.service.message.DefaultMessageTemplateRenderer;
import com.bct.ngtpa.apiservice.application.service.message.MessageTemplateRenderCommand;
import com.bct.ngtpa.apiservice.application.service.message.MessageTemplateRenderer;
import java.util.Objects;
import reactor.core.publisher.Mono;

public class MessageDeliveryAdapter implements MessageDeliveryPort {
    private static final String SENDER_RESULT_MISSING = "SENDER_RESULT_MISSING";
    private static final String SENDER_RESULT_MISSING_MESSAGE = "Sender result is missing";
    private final SmsSenderPort smsSenderPort;
    private final EmailSenderPort emailSenderPort;
    private final MessageTemplateRenderer messageTemplateRenderer;

    public MessageDeliveryAdapter(SmsSenderPort smsSenderPort, EmailSenderPort emailSenderPort) {
        this(smsSenderPort, emailSenderPort, new DefaultMessageTemplateRenderer());
    }

    public MessageDeliveryAdapter(
            SmsSenderPort smsSenderPort,
            EmailSenderPort emailSenderPort,
            MessageTemplateRenderer messageTemplateRenderer) {
        this.smsSenderPort = Objects.requireNonNull(smsSenderPort);
        this.emailSenderPort = Objects.requireNonNull(emailSenderPort);
        this.messageTemplateRenderer = Objects.requireNonNull(messageTemplateRenderer);
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

    private SmsMessage toSmsMessage(MessageDeliveryCommand command) {
        return new SmsMessage(command.recipient(), render(command.body(), command), null, command.correlationId());
    }

    private EmailMessage toEmailMessage(MessageDeliveryCommand command) {
        return new EmailMessage(
                command.recipient(),
                render(command.subject(), command),
                render(command.body(), command),
                null,
                command.correlationId());
    }

    private String render(String template, MessageDeliveryCommand command) {
        return messageTemplateRenderer.render(new MessageTemplateRenderCommand(template, command.templateVariables()))
                .renderedText();
    }

    private MessageDeliveryResult toDeliveryResult(MessageDeliveryChannel channel, SenderResult result) {
        if (result == null) {
            return new MessageDeliveryResult(MessageDeliveryStatus.FAILED, channel, null,
                    SENDER_RESULT_MISSING, SENDER_RESULT_MISSING_MESSAGE);
        }
        return new MessageDeliveryResult(mapStatus(result.status()), channel, result.providerMessageId(),
                result.failureCode(), result.failureMessage());
    }

    private MessageDeliveryStatus mapStatus(SenderStatus status) {
        return status == SenderStatus.ACCEPTED ? MessageDeliveryStatus.ACCEPTED : MessageDeliveryStatus.FAILED;
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

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
