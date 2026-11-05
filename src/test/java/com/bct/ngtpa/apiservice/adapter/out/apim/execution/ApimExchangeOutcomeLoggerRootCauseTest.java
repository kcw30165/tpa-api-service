package com.bct.ngtpa.apiservice.adapter.out.apim.execution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.bct.ngtpa.apiservice.adapter.out.apim.ApimResponseMappingException;
import com.bct.ngtpa.apiservice.adapter.out.apim.credential.ApimCredentialResolutionContext;
import com.bct.ngtpa.apiservice.adapter.out.apim.credential.ApimDatabaseScope;
import com.bct.ngtpa.apiservice.adapter.out.apim.crypto.ApimCryptoException;
import com.bct.ngtpa.apiservice.adapter.out.apim.dto.ApimResponseEnvelope;
import com.bct.ngtpa.apiservice.adapter.out.apim.retry.ApimRetryEligibility;
import com.bct.ngtpa.apiservice.application.dto.ActorUserRole;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

/**
 * {@code apim.exchange.failed} names the innermost cause type and, for JSON mapping failures, the
 * field path. Response values never reach the log, even when the Jackson message contains them.
 */
class ApimExchangeOutcomeLoggerRootCauseTest {

  private static final String PATH = "/ws/NGTPA/v1/Test";
  private static final String SECRET = "SECRET-VALUE-123";
  private static final ObjectMapper MAPPER = new ObjectMapper();

  private final ApimExchangeOutcomeLogger outcomeLogger = new ApimExchangeOutcomeLogger(MAPPER);

  private ListAppender<ILoggingEvent> appender;
  private Logger logbackLogger;

  public record StrictItem(
      @JsonProperty("known") String known, @JsonProperty("count") Integer count) {}

  @BeforeEach
  void attachAppender() {
    logbackLogger = (Logger) LoggerFactory.getLogger(ApimExchangeOutcomeLogger.class);
    appender = new ListAppender<>();
    appender.start();
    logbackLogger.addAppender(appender);
  }

  @AfterEach
  void detachAppender() {
    logbackLogger.detachAppender(appender);
  }

  @Test
  void unknownPropertyLogsRootCauseTypeAndFieldPathWithoutValues() {
    Throwable cause = parseFailure("\"extra\":\"" + SECRET + "\"");

    outcomeLogger.logExchangeFailure(request(), new ApimResponseMappingException(cause), "req-1");

    assertThat(single())
        .contains("\"exceptionType\":\"ApimResponseMappingException\"")
        .doesNotContain("errorMessage", "Failed to map APIM response payload.")
        .contains("\"rootCauseType\":\"UnrecognizedPropertyException\"")
        .contains("\"jsonPath\":\"response.data[0].extra\"")
        .doesNotContain(SECRET);
  }

  @Test
  void wrongValueTypeNeverLogsTheRejectedValue() {
    Throwable cause = parseFailure("\"count\":\"" + SECRET + "\"");
    assertThat(cause.getMessage()).contains(SECRET);

    outcomeLogger.logExchangeFailure(request(), new ApimResponseMappingException(cause), "req-1");

    assertThat(single())
        .contains("\"rootCauseType\":\"InvalidFormatException\"")
        .contains("\"jsonPath\":\"response.data[0].count\"")
        .doesNotContain(SECRET);
  }

  @Test
  void nestedCryptoFailureLogsRootCauseTypeWithoutPath() {
    var failure =
        new ApimCryptoException(
            "Failed to decrypt APIM response payload.",
            new ApimCryptoException("Failed to decrypt APIM field payload."));

    outcomeLogger.logExchangeFailure(request(), failure, "req-1");

    assertThat(single())
        .contains("\"exceptionType\":\"ApimCryptoException\"")
        .contains("\"rootCauseType\":\"ApimCryptoException\"")
        .doesNotContain("jsonPath");
  }

  @Test
  void failureWithoutCauseAddsNeitherField() {
    outcomeLogger.logExchangeFailure(request(), new IllegalStateException("boom"), "req-1");

    assertThat(single()).doesNotContain("rootCauseType").doesNotContain("jsonPath");
  }

  @Test
  void cyclicCauseChainTerminates() {
    var first = new CyclicFailure("first");
    var second = new CyclicFailure("second");
    first.next = second;
    second.next = first;

    outcomeLogger.logExchangeFailure(request(), first, "req-1");

    assertThat(single()).contains("apim.exchange.failed").doesNotContain("jsonPath");
  }

  private static Throwable parseFailure(String itemField) {
    JavaType type =
        MAPPER
            .getTypeFactory()
            .constructParametricType(ApimResponseEnvelope.class, StrictItem.class);
    String json =
        "{\"response\":{\"err-message\":\"\",\"data\":[{\"known\":\"a\"," + itemField + "}]}}";
    Throwable error = catchThrowable(() -> MAPPER.readValue(json, type));
    assertThat(error).isNotNull();
    return error;
  }

  private String single() {
    assertThat(appender.list).hasSize(1);
    ILoggingEvent event = appender.list.get(0);
    assertThat(event.getLevel()).isEqualTo(Level.ERROR);
    return event.getFormattedMessage();
  }

  private static ApimExchangeRequest<String, String> request() {
    return new ApimExchangeRequest<>(
        PATH,
        "body",
        String.class,
        String.class,
        ApimDatabaseScope.ACCOUNT_ENV,
        new ApimCredentialResolutionContext(ActorUserRole.ROLE_USER_MBR, "DEV"),
        ApimRetryEligibility.SAFE_BUSINESS_CALL);
  }

  private static final class CyclicFailure extends RuntimeException {
    private static final long serialVersionUID = 1L;
    private RuntimeException next;

    CyclicFailure(String message) {
      super(message);
    }

    @Override
    public synchronized Throwable getCause() {
      return next;
    }
  }
}
