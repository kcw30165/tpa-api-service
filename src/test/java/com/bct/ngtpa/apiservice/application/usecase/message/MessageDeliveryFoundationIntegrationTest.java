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
import com.bct.ngtpa.apiservice.application.service.message.DefaultMessageTemplateRenderer;
import com.bct.ngtpa.apiservice.application.service.message.MessageTemplateRenderCommand;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class MessageDeliveryFoundationIntegrationTest {

    @Test
    void messageDeliveryFoundationDispatchesSmsWithRenderedContent() {
        AtomicInteger smsCalls = new AtomicInteger();
        AtomicInteger emailCalls = new AtomicInteger();
        AtomicReference<SmsMessage> capturedSms = new AtomicReference<>();
        MessageDeliveryAdapter service = new MessageDeliveryAdapter(
                message -> {
                    smsCalls.incrementAndGet();
                    capturedSms.set(message);
                    return Mono.just(new SenderResult(SenderStatus.ACCEPTED, null, null, null));
                },
                message -> {
                    emailCalls.incrementAndGet();
                    return Mono.just(new SenderResult(SenderStatus.ACCEPTED, null, null, null));
                });

        StepVerifier.create(service.deliver(new MessageDeliveryCommand(
                        MessageDeliveryType.REGISTRATION_OTP,
                        MessageDeliveryScenario.REGISTRATION,
                        MessageDeliveryChannel.SMS,
                        "91234567",
                        null,
                        "Hello {name}",
                        null,
                        Map.of("name", "Member"),
                        "en",
                        "req-123")))
                .assertNext(result -> assertThat(result.status()).isEqualTo(MessageDeliveryStatus.ACCEPTED))
                .verifyComplete();

        assertThat(smsCalls).hasValue(1);
        assertThat(emailCalls).hasValue(0);
        assertThat(capturedSms.get().content()).isEqualTo("Hello Member");
        assertThat(capturedSms.get().recipient()).isEqualTo("91234567");
    }

    @Test
    void messageDeliveryFoundationDispatchesEmailWithRenderedSubjectAndBody() {
        AtomicInteger smsCalls = new AtomicInteger();
        AtomicInteger emailCalls = new AtomicInteger();
        AtomicReference<EmailMessage> capturedEmail = new AtomicReference<>();
        MessageDeliveryAdapter service = new MessageDeliveryAdapter(
                message -> {
                    smsCalls.incrementAndGet();
                    return Mono.just(new SenderResult(SenderStatus.ACCEPTED, null, null, null));
                },
                message -> {
                    emailCalls.incrementAndGet();
                    capturedEmail.set(message);
                    return Mono.just(new SenderResult(SenderStatus.ACCEPTED, null, null, null));
                });

        StepVerifier.create(service.deliver(new MessageDeliveryCommand(
                        MessageDeliveryType.REGISTRATION_OTP,
                        MessageDeliveryScenario.REGISTRATION,
                        MessageDeliveryChannel.EMAIL,
                        "member@example.com",
                        "Notice {refNo}",
                        "Hello {name}",
                        null,
                        Map.of("refNo", "ABC123", "name", "Member"),
                        "en",
                        "req-123")))
                .assertNext(result -> assertThat(result.status()).isEqualTo(MessageDeliveryStatus.ACCEPTED))
                .verifyComplete();

        assertThat(smsCalls).hasValue(0);
        assertThat(emailCalls).hasValue(1);
        assertThat(capturedEmail.get().subject()).isEqualTo("Notice ABC123");
        assertThat(capturedEmail.get().body()).isEqualTo("Hello Member");
        assertThat(capturedEmail.get().recipient()).isEqualTo("member@example.com");
    }

    @Test
    void messageDeliveryFoundationMapsSenderFailureSafely() {
        EmailSenderPort failingEmailSender = message -> Mono.just(new SenderResult(
                SenderStatus.FAILED,
                null,
                "PROVIDER_DOWN",
                "provider unavailable"));
        MessageDeliveryAdapter service = new MessageDeliveryAdapter(
                message -> Mono.just(new SenderResult(SenderStatus.ACCEPTED, null, null, null)),
                failingEmailSender);

        StepVerifier.create(service.deliver(new MessageDeliveryCommand(
                        MessageDeliveryType.REGISTRATION_OTP,
                        MessageDeliveryScenario.REGISTRATION,
                        MessageDeliveryChannel.EMAIL,
                        "member@example.com",
                        "Notice ABC123",
                        "Your code is 123456",
                        null,
                        Map.of(),
                        "en",
                        "req-123")))
                .assertNext(result -> {
                    assertThat(result.status()).isEqualTo(MessageDeliveryStatus.FAILED);
                    assertThat(result.failureCode()).isEqualTo("MESSAGE_DELIVERY_REJECTED");
                    assertSafeFailure(result);
                })
                .verifyComplete();
    }

    @Test
    void messageDeliveryFoundationDoesNotRequireOtpTypes() {
        Set<String> forbiddenNames = Set.of(
                "OtpChallengePort",
                "OtpChallengeStatePort",
                "OtpChallenge",
                "OtpCodeGenerator",
                "OtpCodeHasher");

        for (Constructor<?> constructor : MessageDeliveryAdapter.class.getDeclaredConstructors()) {
            for (Class<?> parameterType : constructor.getParameterTypes()) {
                assertThat(forbiddenNames).doesNotContain(parameterType.getSimpleName());
            }
        }
        for (Field field : MessageDeliveryAdapter.class.getDeclaredFields()) {
            assertThat(forbiddenNames).doesNotContain(field.getType().getSimpleName());
        }
    }

    @Test
    void messageTemplateRendererDoesNotPerformTtlConversionOrUnitAppend() {
        String rendered = new DefaultMessageTemplateRenderer()
                .render(new MessageTemplateRenderCommand("Valid for {ttlValue}", Map.of("ttlValue", "5")))
                .renderedText();

        assertThat(rendered).isEqualTo("Valid for 5");
        assertThat(rendered).isNotEqualTo("Valid for 5 minutes");
    }

    private void assertSafeFailure(MessageDeliveryResult result) {
        String output = String.join("\n",
                String.valueOf(result.failureCode()),
                String.valueOf(result.failureMessage()),
                String.valueOf(result.providerMessageId()));
        assertThat(output).doesNotContain(
                "member@example.com",
                "91234567",
                "Your code is 123456",
                "123456",
                "sms-password",
                "smtp-password");
    }
}
