package com.bct.ngtpa.apiservice.application.usecase.message;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.application.dto.message.EmailMessage;
import com.bct.ngtpa.apiservice.application.dto.message.MessageDeliveryChannel;
import com.bct.ngtpa.apiservice.application.dto.message.MessageDeliveryCommand;
import com.bct.ngtpa.apiservice.application.dto.message.MessageDeliveryResult;
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
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class MessageDeliveryServiceTest {

    @Test
    void dispatchesSmsCommandToSmsSender() {
        FakeSmsSenderPort smsSenderPort = new FakeSmsSenderPort(message -> Mono.just(new SenderResult(SenderStatus.ACCEPTED, "sms-123", null, null)));
        FakeEmailSenderPort emailSenderPort = new FakeEmailSenderPort(message -> Mono.just(new SenderResult(SenderStatus.ACCEPTED, "email-123", null, null)));
        MessageDeliveryService service = new MessageDeliveryService(smsSenderPort, emailSenderPort);

        StepVerifier.create(service.deliver(smsCommand(null)))
                .assertNext(result -> assertThat(result).isEqualTo(new MessageDeliveryResult(
                        MessageDeliveryStatus.ACCEPTED,
                        MessageDeliveryChannel.SMS,
                        "sms-123",
                        null,
                        null)))
                .verifyComplete();

        assertThat(smsSenderPort.invocationCount.get()).isEqualTo(1);
        assertThat(emailSenderPort.invocationCount.get()).isZero();
        assertThat(smsSenderPort.lastMessage.get()).isEqualTo(new SmsMessage(
                "91234567",
                "Your code is 123456",
                null,
                "req-sms-123"));
    }

    @Test
    void dispatchesEmailCommandToEmailSender() {
        FakeSmsSenderPort smsSenderPort = new FakeSmsSenderPort(message -> Mono.just(new SenderResult(SenderStatus.ACCEPTED, "sms-123", null, null)));
        FakeEmailSenderPort emailSenderPort = new FakeEmailSenderPort(message -> Mono.just(new SenderResult(SenderStatus.ACCEPTED, "email-123", null, null)));
        MessageDeliveryService service = new MessageDeliveryService(smsSenderPort, emailSenderPort);

        StepVerifier.create(service.deliver(emailCommand("BCT verification code")))
                .assertNext(result -> assertThat(result).isEqualTo(new MessageDeliveryResult(
                        MessageDeliveryStatus.ACCEPTED,
                        MessageDeliveryChannel.EMAIL,
                        "email-123",
                        null,
                        null)))
                .verifyComplete();

        assertThat(emailSenderPort.invocationCount.get()).isEqualTo(1);
        assertThat(smsSenderPort.invocationCount.get()).isZero();
        assertThat(emailSenderPort.lastMessage.get()).isEqualTo(new EmailMessage(
                "member@example.com",
                "BCT verification code",
                "Your code is 123456",
                null,
                "req-email-123"));
    }

    @Test
    void mapsAcceptedSmsSenderResultToMessageDeliveryResult() {
        MessageDeliveryService service = new MessageDeliveryService(
                new FakeSmsSenderPort(message -> Mono.just(new SenderResult(SenderStatus.ACCEPTED, "sms-123", null, null))),
                new FakeEmailSenderPort(message -> Mono.just(new SenderResult(SenderStatus.ACCEPTED, "email-123", null, null))));

        StepVerifier.create(service.deliver(smsCommand(null)))
                .assertNext(result -> {
                    assertThat(result.status()).isEqualTo(MessageDeliveryStatus.ACCEPTED);
                    assertThat(result.channel()).isEqualTo(MessageDeliveryChannel.SMS);
                    assertThat(result.providerMessageId()).isEqualTo("sms-123");
                })
                .verifyComplete();
    }

    @Test
    void mapsFailedEmailSenderResultToMessageDeliveryResult() {
        MessageDeliveryService service = new MessageDeliveryService(
                new FakeSmsSenderPort(message -> Mono.just(new SenderResult(SenderStatus.ACCEPTED, "sms-123", null, null))),
                new FakeEmailSenderPort(message -> Mono.just(new SenderResult(SenderStatus.FAILED, null, "SMTP_DOWN", "SMTP server unavailable"))));

        StepVerifier.create(service.deliver(emailCommand("BCT verification code")))
                .assertNext(result -> assertThat(result).isEqualTo(new MessageDeliveryResult(
                        MessageDeliveryStatus.FAILED,
                        MessageDeliveryChannel.EMAIL,
                        null,
                        "SMTP_DOWN",
                        "SMTP server unavailable")))
                .verifyComplete();
    }

    @Test
    void rejectsNullCommand() {
        MessageDeliveryService service = new MessageDeliveryService(
                new FakeSmsSenderPort(message -> Mono.just(new SenderResult(SenderStatus.ACCEPTED, null, null, null))),
                new FakeEmailSenderPort(message -> Mono.just(new SenderResult(SenderStatus.ACCEPTED, null, null, null))));

        StepVerifier.create(service.deliver(null))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(IllegalArgumentException.class);
                    assertThat(error).hasMessage("command must not be null");
                })
                .verify();
    }

    @ParameterizedTest
    @MethodSource("invalidCommands")
    void rejectsMissingRequiredCommandFields(MessageDeliveryCommand command, String expectedMessage) {
        MessageDeliveryService service = new MessageDeliveryService(
                new FakeSmsSenderPort(message -> Mono.just(new SenderResult(SenderStatus.ACCEPTED, null, null, null))),
                new FakeEmailSenderPort(message -> Mono.just(new SenderResult(SenderStatus.ACCEPTED, null, null, null))));

        StepVerifier.create(service.deliver(command))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(IllegalArgumentException.class);
                    assertThat(error).hasMessage(expectedMessage);
                })
                .verify();
    }

    @Test
    void rejectsEmailWithoutSubject() {
        MessageDeliveryService service = new MessageDeliveryService(
                new FakeSmsSenderPort(message -> Mono.just(new SenderResult(SenderStatus.ACCEPTED, null, null, null))),
                new FakeEmailSenderPort(message -> Mono.just(new SenderResult(SenderStatus.ACCEPTED, null, null, null))));

        StepVerifier.create(service.deliver(emailCommand("   ")))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(IllegalArgumentException.class);
                    assertThat(error).hasMessage("subject must not be blank for EMAIL");
                })
                .verify();
    }

    @Test
    void allowsSmsWithoutSubject() {
        MessageDeliveryService service = new MessageDeliveryService(
                new FakeSmsSenderPort(message -> Mono.just(new SenderResult(SenderStatus.ACCEPTED, "sms-123", null, null))),
                new FakeEmailSenderPort(message -> Mono.just(new SenderResult(SenderStatus.ACCEPTED, "email-123", null, null))));

        StepVerifier.create(service.deliver(smsCommand("   ")))
                .assertNext(result -> assertThat(result.status()).isEqualTo(MessageDeliveryStatus.ACCEPTED))
                .verifyComplete();
    }

    @Test
    void senderErrorIsPropagatedSafely() {
        RuntimeException failure = new RuntimeException("downstream failed");
        MessageDeliveryService service = new MessageDeliveryService(
                new FakeSmsSenderPort(message -> Mono.error(failure)),
                new FakeEmailSenderPort(message -> Mono.just(new SenderResult(SenderStatus.ACCEPTED, "email-123", null, null))));

        StepVerifier.create(service.deliver(smsCommand(null)))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isSameAs(failure);
                    assertThat(error.getMessage()).doesNotContain("91234567");
                    assertThat(error.getMessage()).doesNotContain("Your code is 123456");
                })
                .verify();
    }

    private static Stream<Arguments> invalidCommands() {
        return Stream.of(
                Arguments.of(new MessageDeliveryCommand(
                        null,
                        MessageDeliveryScenario.REGISTRATION,
                        MessageDeliveryChannel.SMS,
                        "91234567",
                        null,
                        "Your code is 123456",
                        "registration-otp",
                        Map.of(),
                        "en-HK",
                        "req-sms-123"), "messageType must not be null"),
                Arguments.of(new MessageDeliveryCommand(
                        MessageDeliveryType.REGISTRATION_OTP,
                        null,
                        MessageDeliveryChannel.SMS,
                        "91234567",
                        null,
                        "Your code is 123456",
                        "registration-otp",
                        Map.of(),
                        "en-HK",
                        "req-sms-123"), "scenario must not be null"),
                Arguments.of(new MessageDeliveryCommand(
                        MessageDeliveryType.REGISTRATION_OTP,
                        MessageDeliveryScenario.REGISTRATION,
                        null,
                        "91234567",
                        null,
                        "Your code is 123456",
                        "registration-otp",
                        Map.of(),
                        "en-HK",
                        "req-sms-123"), "channel must not be null"),
                Arguments.of(new MessageDeliveryCommand(
                        MessageDeliveryType.REGISTRATION_OTP,
                        MessageDeliveryScenario.REGISTRATION,
                        MessageDeliveryChannel.SMS,
                        "   ",
                        null,
                        "Your code is 123456",
                        "registration-otp",
                        Map.of(),
                        "en-HK",
                        "req-sms-123"), "recipient must not be blank"),
                Arguments.of(new MessageDeliveryCommand(
                        MessageDeliveryType.REGISTRATION_OTP,
                        MessageDeliveryScenario.REGISTRATION,
                        MessageDeliveryChannel.SMS,
                        "91234567",
                        null,
                        "   ",
                        "registration-otp",
                        Map.of(),
                        "en-HK",
                        "req-sms-123"), "body must not be blank"));
    }

    private static MessageDeliveryCommand smsCommand(String subject) {
        return new MessageDeliveryCommand(
                MessageDeliveryType.REGISTRATION_OTP,
                MessageDeliveryScenario.REGISTRATION,
                MessageDeliveryChannel.SMS,
                "91234567",
                subject,
                "Your code is 123456",
                "registration-otp",
                Map.of("code", "123456"),
                "en-HK",
                "req-sms-123");
    }

    private static MessageDeliveryCommand emailCommand(String subject) {
        return new MessageDeliveryCommand(
                MessageDeliveryType.REGISTRATION_OTP,
                MessageDeliveryScenario.REGISTRATION,
                MessageDeliveryChannel.EMAIL,
                "member@example.com",
                subject,
                "Your code is 123456",
                "registration-otp",
                Map.of("code", "123456"),
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