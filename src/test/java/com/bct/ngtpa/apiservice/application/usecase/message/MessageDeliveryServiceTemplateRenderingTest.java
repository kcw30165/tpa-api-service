package com.bct.ngtpa.apiservice.application.usecase.message;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.application.dto.message.EmailMessage;
import com.bct.ngtpa.apiservice.application.dto.message.MessageDeliveryChannel;
import com.bct.ngtpa.apiservice.application.dto.message.MessageDeliveryCommand;
import com.bct.ngtpa.apiservice.application.dto.message.MessageDeliveryScenario;
import com.bct.ngtpa.apiservice.application.dto.message.MessageDeliveryStatus;
import com.bct.ngtpa.apiservice.application.dto.message.MessageDeliveryType;
import com.bct.ngtpa.apiservice.application.dto.message.SenderResult;
import com.bct.ngtpa.apiservice.application.dto.message.SenderStatus;
import com.bct.ngtpa.apiservice.application.dto.message.SmsMessage;
import com.bct.ngtpa.apiservice.application.port.out.EmailSenderPort;
import com.bct.ngtpa.apiservice.application.port.out.SmsSenderPort;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class MessageDeliveryServiceTemplateRenderingTest {

    @Test
    void dispatchesSmsWithRenderedBody() {
        FakeSmsSenderPort smsSenderPort = new FakeSmsSenderPort(message -> Mono.just(new SenderResult(SenderStatus.ACCEPTED, "sms-123", null, null)));
        FakeEmailSenderPort emailSenderPort = new FakeEmailSenderPort(message -> Mono.just(new SenderResult(SenderStatus.ACCEPTED, "email-123", null, null)));
        MessageDeliveryAdapter service = new MessageDeliveryAdapter(smsSenderPort, emailSenderPort);

        StepVerifier.create(service.deliver(smsCommand("Hello {name}", Map.of("name", "Member"))))
                .assertNext(result -> assertThat(result.status()).isEqualTo(MessageDeliveryStatus.ACCEPTED))
                .verifyComplete();

        assertThat(smsSenderPort.lastMessage.get()).isEqualTo(new SmsMessage(
                "91234567",
                "Hello Member",
                null,
                "req-sms-123"));
    }

    @Test
    void dispatchesEmailWithRenderedSubjectAndBody() {
        FakeSmsSenderPort smsSenderPort = new FakeSmsSenderPort(message -> Mono.just(new SenderResult(SenderStatus.ACCEPTED, "sms-123", null, null)));
        FakeEmailSenderPort emailSenderPort = new FakeEmailSenderPort(message -> Mono.just(new SenderResult(SenderStatus.ACCEPTED, "email-123", null, null)));
        MessageDeliveryAdapter service = new MessageDeliveryAdapter(smsSenderPort, emailSenderPort);

        StepVerifier.create(service.deliver(emailCommand(
                        "Notice {refNo}",
                        "Hello {name}",
                        Map.of("refNo", "ABC123", "name", "Member"))))
                .assertNext(result -> assertThat(result.status()).isEqualTo(MessageDeliveryStatus.ACCEPTED))
                .verifyComplete();

        assertThat(emailSenderPort.lastMessage.get()).isEqualTo(new EmailMessage(
                "member@example.com",
                "Notice ABC123",
                "Hello Member",
                null,
                "req-email-123"));
    }

    @Test
    void doesNotDispatchWhenTemplateVariableMissing() {
        FakeSmsSenderPort smsSenderPort = new FakeSmsSenderPort(message -> Mono.just(new SenderResult(SenderStatus.ACCEPTED, "sms-123", null, null)));
        FakeEmailSenderPort emailSenderPort = new FakeEmailSenderPort(message -> Mono.just(new SenderResult(SenderStatus.ACCEPTED, "email-123", null, null)));
        MessageDeliveryAdapter service = new MessageDeliveryAdapter(smsSenderPort, emailSenderPort);

        StepVerifier.create(service.deliver(smsCommand("Hello {name}", Map.of())))
                .assertNext(result -> {
                    assertThat(result.status()).isEqualTo(MessageDeliveryStatus.FAILED);
                    assertThat(result.failureCode()).isEqualTo("MESSAGE_DELIVERY_REJECTED");
                    assertThat(result.failureMessage()).isEqualTo("Message delivery was not accepted");
                })
                .verifyComplete();

        assertThat(smsSenderPort.invocationCount.get()).isZero();
        assertThat(emailSenderPort.invocationCount.get()).isZero();
    }

    @Test
    void templateRenderingFailureDoesNotExposeBodyOrRecipient() {
        FakeSmsSenderPort smsSenderPort = new FakeSmsSenderPort(message -> Mono.just(new SenderResult(SenderStatus.ACCEPTED, "sms-123", null, null)));
        FakeEmailSenderPort emailSenderPort = new FakeEmailSenderPort(message -> Mono.just(new SenderResult(SenderStatus.ACCEPTED, "email-123", null, null)));
        MessageDeliveryAdapter service = new MessageDeliveryAdapter(smsSenderPort, emailSenderPort);

        StepVerifier.create(service.deliver(smsCommand("Reference {code}", Map.of())))
                .assertNext(result -> {
                    assertThat(result.status()).isEqualTo(MessageDeliveryStatus.FAILED);
                    assertThat(result.failureCode()).isEqualTo("MESSAGE_DELIVERY_REJECTED");
                    assertThat(result.toString()).doesNotContain("91234567", "Reference {code}", "code");
                })
                .verifyComplete();

        assertThat(smsSenderPort.invocationCount.get()).isZero();
        assertThat(emailSenderPort.invocationCount.get()).isZero();
    }

    private static MessageDeliveryCommand smsCommand(String body, Map<String, String> templateVariables) {
        return new MessageDeliveryCommand(
                MessageDeliveryType.REGISTRATION_OTP,
                MessageDeliveryScenario.REGISTRATION,
                MessageDeliveryChannel.SMS,
                "91234567",
                null,
                body,
                "neutral-template",
                templateVariables,
                "en-HK",
                "req-sms-123");
    }

    private static MessageDeliveryCommand emailCommand(String subject, String body, Map<String, String> templateVariables) {
        return new MessageDeliveryCommand(
                MessageDeliveryType.REGISTRATION_OTP,
                MessageDeliveryScenario.REGISTRATION,
                MessageDeliveryChannel.EMAIL,
                "member@example.com",
                subject,
                body,
                "neutral-template",
                templateVariables,
                "en-HK",
                "req-email-123");
    }

    private record FakeSmsSenderPort(SenderBehavior behavior, AtomicInteger invocationCount, AtomicReference<SmsMessage> lastMessage)
            implements SmsSenderPort {

        private FakeSmsSenderPort(SenderBehavior behavior) {
            this(behavior, new AtomicInteger(), new AtomicReference<>());
        }

        @Override
        public Mono<SenderResult> sendSms(SmsMessage message) {
            invocationCount.incrementAndGet();
            lastMessage.set(message);
            return behavior.apply(message);
        }
    }

    private record FakeEmailSenderPort(EmailBehavior behavior, AtomicInteger invocationCount, AtomicReference<EmailMessage> lastMessage)
            implements EmailSenderPort {

        private FakeEmailSenderPort(EmailBehavior behavior) {
            this(behavior, new AtomicInteger(), new AtomicReference<>());
        }

        @Override
        public Mono<SenderResult> sendEmail(EmailMessage message) {
            invocationCount.incrementAndGet();
            lastMessage.set(message);
            return behavior.apply(message);
        }
    }

    @FunctionalInterface
    private interface SenderBehavior {
        Mono<SenderResult> apply(SmsMessage message);
    }

    @FunctionalInterface
    private interface EmailBehavior {
        Mono<SenderResult> apply(EmailMessage message);
    }
}