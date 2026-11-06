package com.bct.ngtpa.apiservice.adapter.out.apim.execution;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.bct.ngtpa.apiservice.adapter.out.apim.credential.ApimCredentialResolutionContext;
import com.bct.ngtpa.apiservice.adapter.out.apim.credential.ApimDatabaseScope;
import com.bct.ngtpa.apiservice.adapter.out.apim.dto.ApimResponseBody;
import com.bct.ngtpa.apiservice.adapter.out.apim.dto.ApimResponseEnvelope;
import com.bct.ngtpa.apiservice.adapter.out.apim.retry.ApimRetryEligibility;
import com.bct.ngtpa.apiservice.application.dto.ActorUserRole;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

class ApimExchangeOutcomeLoggerTest {

  private static final String PATH = "/ws/NGTPA/v1/Test";
  private static final ApimCredentialResolutionContext CONTEXT =
      new ApimCredentialResolutionContext(ActorUserRole.ROLE_USER_MBR, "DEV");

  private final ObjectMapper objectMapper = new ObjectMapper();
  private final ApimExchangeOutcomeLogger outcomeLogger =
      new ApimExchangeOutcomeLogger(objectMapper);

  private ListAppender<ILoggingEvent> appender;
  private Logger logbackLogger;

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
  void wellFormedSuccessEnvelopeLogsNothing() {
    outcomeLogger.logSuccessOutcome(request(), envelope("", List.of("x")), "req-1");
    assertThat(appender.list).isEmpty();
  }

  @Test
  void malformedMissingResponseLogsContractInvalidAtError() {
    outcomeLogger.logSuccessOutcome(request(), null, "req-1");
    assertThat(appender.list).hasSize(1);
    ILoggingEvent event = appender.list.get(0);
    assertThat(event.getLevel()).isEqualTo(Level.ERROR);
    assertThat(event.getFormattedMessage()).contains("apim.contract.invalid");
    assertThat(event.getFormattedMessage()).contains("req-1");
    assertThat(event.getFormattedMessage()).contains(PATH);
  }

  @Test
  void malformedMissingDataLogsContractInvalidAtError() {
    outcomeLogger.logSuccessOutcome(request(), envelope("", null), "req-1");
    assertThat(appender.list).hasSize(1);
    assertThat(appender.list.get(0).getLevel()).isEqualTo(Level.ERROR);
    assertThat(appender.list.get(0).getFormattedMessage()).contains("apim.contract.invalid");
  }

  @Test
  void malformedEmptyDataArrayLogsContractInvalidAtError() {
    // Regression test for the TRPMemberLogin production incident: an empty data array combined
    // with an empty err-message must be logged as apim.contract.invalid, not silently treated as
    // success. See architecture-and-design.md section 12.
    outcomeLogger.logSuccessOutcome(request(), envelope("", List.of()), "req-1");
    assertThat(appender.list).hasSize(1);
    ILoggingEvent event = appender.list.get(0);
    assertThat(event.getLevel()).isEqualTo(Level.ERROR);
    assertThat(event.getFormattedMessage()).contains("apim.contract.invalid");
    assertThat(event.getFormattedMessage())
        .contains("response.data must be non-empty when response.err-message is empty");
  }

  @Test
  void businessRejectionLogsAtWarnWithMetadataOnly() {
    outcomeLogger.logSuccessOutcome(request(), envelope("err.tpa.wrong.password", null), "req-1");
    assertThat(appender.list).hasSize(1);
    ILoggingEvent event = appender.list.get(0);
    assertThat(event.getLevel()).isEqualTo(Level.WARN);
    assertThat(event.getFormattedMessage())
        .contains("apim.business.rejected", PATH, "req-1", "ACCOUNT_ENV", "SAFE_BUSINESS_CALL")
        .doesNotContain("errorCode", "err.tpa.wrong.password");
  }

  @Test
  void codeShapedArbitraryBusinessTextIsNeverLogged() {
    String marker = "err.O17_PRIVATE_PAYLOAD_123456";
    outcomeLogger.logSuccessOutcome(request(), envelope(marker, null), "req-code-shaped");
    assertThat(appender.list).hasSize(1);
    ILoggingEvent event = appender.list.get(0);
    assertThat(event.getLevel()).isEqualTo(Level.WARN);
    assertThat(event.getFormattedMessage())
        .contains("apim.business.rejected", PATH, "req-code-shaped")
        .doesNotContain(marker, "errorCode");
  }

  @Test
  void arbitraryBusinessTextIsNeverLoggedButEstablishedCodeIsRetained() {
    String marker = "O17-ARBITRARY-DOWNSTREAM-TEXT-CIPHERTEXT";
    outcomeLogger.logSuccessOutcome(request(), envelope(marker, null), "req-1");
    assertThat(appender.list).hasSize(1);
    ILoggingEvent event = appender.list.get(0);
    assertThat(event.getLevel()).isEqualTo(Level.WARN);
    assertThat(event.getFormattedMessage())
        .contains("apim.business.rejected")
        .doesNotContain(marker, "errMessage");
  }

  @Test
  void exchangeFailureOmitsThrowableMessagesAndPreservesTypesAndMetadata() {
    String marker = "O17-THROWABLE-PAYLOAD-MARKER";
    outcomeLogger.logExchangeFailure(
        request(),
        new IllegalStateException(marker, new IllegalArgumentException(marker)),
        "req-1");
    assertThat(appender.list).hasSize(1);
    ILoggingEvent event = appender.list.get(0);
    assertThat(event.getLevel()).isEqualTo(Level.ERROR);
    assertThat(event.getFormattedMessage())
        .contains("apim.exchange.failed", "IllegalStateException", "IllegalArgumentException", PATH)
        .doesNotContain(marker, "errorMessage");
    assertThat(event.getThrowableProxy()).isNull();
  }

  @Test
  void businessRejectionWithEmptyDataArrayLogsAtWarnNotError() {
    outcomeLogger.logSuccessOutcome(
        request(), envelope("err.tpa.wrong.password", List.of()), "req-1");
    assertThat(appender.list).hasSize(1);
    assertThat(appender.list.get(0).getLevel()).isEqualTo(Level.WARN);
    assertThat(appender.list.get(0).getFormattedMessage()).contains("apim.business.rejected");
  }

  @Test
  void exchangeFailureLogsAtErrorWithExceptionType() {
    outcomeLogger.logExchangeFailure(request(), new IllegalStateException("boom"), "req-1");
    assertThat(appender.list).hasSize(1);
    ILoggingEvent event = appender.list.get(0);
    assertThat(event.getLevel()).isEqualTo(Level.ERROR);
    assertThat(event.getFormattedMessage()).contains("apim.exchange.failed");
    assertThat(event.getFormattedMessage()).contains("IllegalStateException");
  }

  @Test
  void missingRequestIdIsOmittedFromPayload() {
    outcomeLogger.logExchangeFailure(request(), new RuntimeException("x"), null);
    assertThat(appender.list.get(0).getFormattedMessage()).doesNotContain("\"requestId\"");
  }

  private ApimExchangeRequest<String, String> request() {
    return new ApimExchangeRequest<>(
        PATH,
        "body",
        String.class,
        String.class,
        ApimDatabaseScope.ACCOUNT_ENV,
        CONTEXT,
        ApimRetryEligibility.SAFE_BUSINESS_CALL);
  }

  private ApimResponseEnvelope<String> envelope(String errMessage, List<String> data) {
    return new ApimResponseEnvelope<>(new ApimResponseBody<>(errMessage, data));
  }
  @Test
  void terminalOwnedExchangeFailureLogsWarnWithoutThrowableOrMessage() {
    String marker = "R1A-TERMINAL-THROWABLE-MARKER";
    outcomeLogger.logExchangeFailure(terminalRequest(), new IllegalStateException(marker), "req-t");
    assertThat(appender.list).hasSize(1);
    ILoggingEvent event = appender.list.get(0);
    assertThat(event.getLevel()).isEqualTo(Level.WARN);
    assertThat(event.getFormattedMessage()).contains("apim.exchange.failed").doesNotContain(marker, "errorMessage");
    assertThat(event.getThrowableProxy()).isNull();
  }

  @Test
  void terminalOwnedContractInvalidLogsWarn() {
    outcomeLogger.logSuccessOutcome(terminalRequest(), envelope("", null), "req-t");
    assertThat(appender.list).hasSize(1);
    assertThat(appender.list.get(0).getLevel()).isEqualTo(Level.WARN);
    assertThat(appender.list.get(0).getFormattedMessage()).contains("apim.contract.invalid");
  }

  @Test
  void terminalOwnedBusinessRejectionRemainsWarn() {
    outcomeLogger.logSuccessOutcome(terminalRequest(), envelope("err.tpa.wrong.password", List.of()), "req-t");
    assertThat(appender.list).hasSize(1);
    assertThat(appender.list.get(0).getLevel()).isEqualTo(Level.WARN);
    assertThat(appender.list.get(0).getFormattedMessage()).contains("apim.business.rejected");
  }

  private ApimExchangeRequest<String, String> terminalRequest() {
    return new ApimExchangeRequest<>(PATH,"body",String.class,String.class,ApimDatabaseScope.ACCOUNT_ENV,CONTEXT,ApimRetryEligibility.SAFE_BUSINESS_CALL,ApimResubmissionPolicy.ALLOW_RECOVERY,ApimIncidentOwnership.TERMINAL_CALLER);
  }

}
