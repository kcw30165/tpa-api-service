package com.bct.ngtpa.apiservice.adapter.out.email;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.bct.ngtpa.apiservice.adapter.out.email.config.EmailProperties;
import com.bct.ngtpa.apiservice.application.dto.message.EmailMessage;
import com.bct.ngtpa.apiservice.application.dto.message.SenderStatus;
import com.bct.ngtpa.apiservice.shared.masking.MaskingProperties;
import com.bct.ngtpa.apiservice.shared.masking.MaskingService;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class SmtpEmailSenderAdapterTest {

  private Logger logger;
  private ListAppender<ILoggingEvent> listAppender;

  @BeforeEach
  void attachAppender() {
    logger = (Logger) LoggerFactory.getLogger(SmtpEmailSenderAdapter.class);
    listAppender = new ListAppender<>();
    listAppender.start();
    logger.addAppender(listAppender);
  }

  @AfterEach
  void detachAppender() {
    logger.detachAppender(listAppender);
    listAppender.stop();
  }

  @Test
  void mapsEmailMessageToSmtpMessage() {
    AtomicReference<SmtpSendCommand> captured = new AtomicReference<>();
    SmtpEmailSenderAdapter adapter =
        adapterWith(
            baseProperties(),
            command -> {
              captured.set(command);
              return Mono.empty();
            });

    StepVerifier.create(
            adapter.sendEmail(
                new EmailMessage(
                    "member@example.com",
                    "Notice ABC123",
                    "Hello Member",
                    "noreply@example.com",
                    "req-123")))
        .assertNext(result -> assertThat(result.status()).isEqualTo(SenderStatus.ACCEPTED))
        .verifyComplete();

    assertThat(captured.get().to()).isEqualTo("member@example.com");
    assertThat(captured.get().subject()).isEqualTo("Notice ABC123");
    assertThat(captured.get().body()).isEqualTo("Hello Member");
    assertThat(captured.get().from()).isEqualTo("noreply@example.com");
    assertThat(captured.get().host()).isEqualTo("smtp.example.test");
    assertThat(captured.get().port()).isEqualTo(25);
    assertThat(captured.get().timeoutMillis()).isEqualTo(5000);
  }

  @Test
  void usesConfiguredFromWhenEmailMessageFromBlank() {
    AtomicReference<SmtpSendCommand> captured = new AtomicReference<>();
    SmtpEmailSenderAdapter adapter =
        adapterWith(
            baseProperties(),
            command -> {
              captured.set(command);
              return Mono.empty();
            });

    StepVerifier.create(
            adapter.sendEmail(
                new EmailMessage(
                    "member@example.com", "Notice ABC123", "Hello Member", "   ", "req-123")))
        .assertNext(result -> assertThat(result.status()).isEqualTo(SenderStatus.ACCEPTED))
        .verifyComplete();

    assertThat(captured.get().from()).isEqualTo("configured-from@example.com");
  }

  @Test
  void returnsAcceptedWhenSmtpSendSucceeds() {
    SmtpEmailSenderAdapter adapter = adapterWith(baseProperties(), command -> Mono.empty());

    StepVerifier.create(adapter.sendEmail(messageWithOtpBody()))
        .assertNext(
            result -> {
              assertThat(result.status()).isEqualTo(SenderStatus.ACCEPTED);
              assertThat(result.failureCode()).isNull();
              assertThat(result.failureMessage()).isNull();
            })
        .verifyComplete();
  }

  @Test
  void returnsFailedOrPropagatesSafeErrorWhenSmtpSendFails() {
    SmtpEmailSenderAdapter adapter =
        adapterWith(
            baseProperties(),
            command ->
                Mono.error(
                    new IllegalStateException(
                        "provider leaked member@example.com Your code is 123456 smtp-password")));

    StepVerifier.create(adapter.sendEmail(messageWithOtpBody()))
        .assertNext(
            result -> {
              assertThat(result.status()).isEqualTo(SenderStatus.UNKNOWN_OUTCOME);
              assertThat(result.failureCode()).isEqualTo("EMAIL_REQUEST_OUTCOME_UNKNOWN");
              assertThat(result.failureMessage())
                  .doesNotContain(
                      "member@example.com", "Your code is 123456", "123456", "smtp-password");
            })
        .verifyComplete();

    String logOutput = logOutput();
    assertThat(logOutput)
        .doesNotContain("member@example.com", "Your code is 123456", "123456", "smtp-password");
    assertThat(logOutput).contains("bodyLogged=false");
  }

  @Test
  void rejectsMissingRequiredConfigWhenEnabled() {
    EmailProperties properties = baseProperties();
    properties.getSmtp().setHost("");
    properties.getSmtp().setFrom("");

    SmtpEmailSenderAdapter adapter = adapterWith(properties, command -> Mono.empty());

    StepVerifier.create(adapter.sendEmail(messageWithOtpBody()))
        .assertNext(
            result -> {
              assertThat(result.status()).isEqualTo(SenderStatus.FAILED);
              assertThat(result.failureCode()).isEqualTo("EMAIL_CONFIG_HOST_MISSING");
              assertThat(result.failureMessage())
                  .doesNotContain(
                      "member@example.com", "Your code is 123456", "123456", "smtp-password");
            })
        .verifyComplete();
  }

  @Test
  void doesNotLogFullEmailBodyOrOtp() {
    SmtpEmailSenderAdapter adapter = adapterWith(baseProperties(), command -> Mono.empty());

    StepVerifier.create(adapter.sendEmail(messageWithOtpBody()))
        .assertNext(result -> assertThat(result.status()).isEqualTo(SenderStatus.ACCEPTED))
        .verifyComplete();

    assertThat(listAppender.list).isNotEmpty();
    String logOutput = logOutput();

    assertThat(logOutput).doesNotContain("member@example.com", "Your code is 123456", "123456");
    assertThat(logOutput).contains("recipientMasked=m****r@example.com", "bodyLogged=false");
  }

  // emailMaskerMasksCommonEmailShape removed: EmailAddressMasker was deleted as part of the
  // centralized masking policy migration (Phase 3). Equivalent and more thorough coverage
  // now lives in MaskingServiceTest.

  @Test
  void nullMessageWithValidConfigurationErrorsWithIllegalArgumentException() {
    SmtpEmailSenderAdapter adapter = adapterWith(baseProperties(), command -> Mono.empty());
    StepVerifier.create(adapter.sendEmail(null))
        .expectError(IllegalArgumentException.class)
        .verify();
  }

  @Test
  void rejectsMissingFromWhenHostConfiguredButFromMissing() {
    EmailProperties properties = baseProperties();
    properties.getSmtp().setFrom("");
    SmtpEmailSenderAdapter adapter = adapterWith(properties, command -> Mono.empty());
    StepVerifier.create(adapter.sendEmail(messageWithOtpBody()))
        .assertNext(
            result -> {
              assertThat(result.status()).isEqualTo(SenderStatus.FAILED);
              assertThat(result.failureCode()).isEqualTo("EMAIL_CONFIG_FROM_MISSING");
            })
        .verifyComplete();
  }

  @Test
  void safeErrorMessageHandlesNullErrorDefensively() throws Exception {
    SmtpEmailSenderAdapter adapter = adapterWith(baseProperties(), command -> Mono.empty());
    var method =
        SmtpEmailSenderAdapter.class.getDeclaredMethod("safeErrorMessage", Throwable.class);
    method.setAccessible(true);
    Object result = method.invoke(adapter, new Object[] {null});
    assertThat(result).isEqualTo("");
  }

  private SmtpEmailSenderAdapter adapterWith(EmailProperties properties, SmtpClient smtpClient) {
    return new SmtpEmailSenderAdapter(
        smtpClient, properties, new MaskingService(new MaskingProperties()));
  }

  private EmailProperties baseProperties() {
    EmailProperties properties = new EmailProperties();
    properties.setEnabled(true);
    properties.getSmtp().setHost("smtp.example.test");
    properties.getSmtp().setPort(25);
    properties.getSmtp().setFrom("configured-from@example.com");
    properties.getSmtp().setTimeoutMillis(5000);
    return properties;
  }

  private EmailMessage messageWithOtpBody() {
    return new EmailMessage(
        "member@example.com",
        "Notice ABC123",
        "Your code is 123456",
        "noreply@example.com",
        "req-123");
  }

  private String logOutput() {
    return listAppender.list.stream()
        .map(ILoggingEvent::getFormattedMessage)
        .reduce("", (left, right) -> left + "\n" + right);
  }
}
