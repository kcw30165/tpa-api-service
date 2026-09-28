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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class EmailDeliverySecurityGuardTest {

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
  void emailAdapterDoesNotLogFullEmailBodyOrOtp() {
    SmtpEmailSenderAdapter adapter =
        new SmtpEmailSenderAdapter(
            command ->
                Mono.error(
                    new IllegalStateException(
                        "provider leaked member@example.com Your code is 123456 smtp-password")),
            baseProperties(),
            new MaskingService(new MaskingProperties()));

    StepVerifier.create(
            adapter.sendEmail(
                new EmailMessage(
                    "member@example.com",
                    "Notice ABC123",
                    "Your code is 123456",
                    "noreply@example.com",
                    "req-123")))
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

  private EmailProperties baseProperties() {
    EmailProperties properties = new EmailProperties();
    properties.setEnabled(true);
    properties.getSmtp().setHost("smtp.example.test");
    properties.getSmtp().setPort(25);
    properties.getSmtp().setFrom("configured-from@example.com");
    properties.getSmtp().setTimeoutMillis(5000);
    return properties;
  }

  private String logOutput() {
    return listAppender.list.stream()
        .map(ILoggingEvent::getFormattedMessage)
        .reduce("", (left, right) -> left + "\n" + right);
  }
}
