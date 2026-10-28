package com.bct.ngtpa.apiservice.adapter.out.apim.execution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.bct.ngtpa.apiservice.adapter.out.apim.ApimPayloadCryptoService;
import com.bct.ngtpa.apiservice.adapter.out.apim.ApimWebClientFacade;
import com.bct.ngtpa.apiservice.adapter.out.apim.config.ApimProperties;
import com.bct.ngtpa.apiservice.adapter.out.apim.credential.ApimCredentialResolutionContext;
import com.bct.ngtpa.apiservice.adapter.out.apim.credential.ApimDatabaseScope;
import com.bct.ngtpa.apiservice.adapter.out.apim.crypto.ApimCryptoException;
import com.bct.ngtpa.apiservice.adapter.out.apim.dto.ApimResponseBody;
import com.bct.ngtpa.apiservice.adapter.out.apim.dto.ApimResponseEnvelope;
import com.bct.ngtpa.apiservice.adapter.out.apim.retry.ApimRetryEligibility;
import com.bct.ngtpa.apiservice.application.dto.ActorUserRole;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * Sub-task 2b.2 STEP 1, Batch 2a (Forgot Password Completion). Executor-to-logger seam: real
 * {@link DefaultApimExchangeExecutor} constructed via its 3-arg legacy constructor (which builds a
 * real, non-mocked {@link ApimExchangeOutcomeLogger} internally - see
 * DefaultApimExchangeExecutor.java lines 47-58), stubbing only {@link ApimWebClientFacade} and
 * {@link ApimPayloadCryptoService}. A {@link ListAppender} is attached to {@link
 * ApimExchangeOutcomeLogger}'s own logger, not this executor's, because that is the class that
 * actually emits the log line (precedent: ApimExchangeOutcomeLoggerTest lines 40-51).
 *
 * <p>Per Nick's 2026-10-28 "one scenario passing before adding the others" instruction, this class
 * currently contains ONLY scenario 1 of 5 (facade error -&gt; one {@code apim.exchange.failed}
 * ERROR). The remaining four scenarios (response-decrypt-throws via {@code ApimCryptoException},
 * non-empty err-message -&gt; {@code apim.business.rejected} WARN, missing response/data -&gt;
 * {@code apim.contract.invalid} ERROR, and well-formed envelope -&gt; no log) are deliberately not
 * yet added. The outcome-logger level assertions for those four classifications already exist and
 * are pinned at the logger-unit level in {@code ApimExchangeOutcomeLoggerTest} lines 53-119; this
 * class exists only to characterize that {@link DefaultApimExchangeExecutor} actually wires
 * reactor signals into that logger, not to repeat those classification-level assertions.
 */
class DefaultApimExchangeExecutorLoggingTest {

  private static final String PATH = "/ws/NGTPA/v1/Test";
  private static final RequestDto SOURCE = new RequestDto("value");
  private static final ApimCredentialResolutionContext CONTEXT =
      new ApimCredentialResolutionContext(ActorUserRole.ROLE_USER_MBR, "DEV");
  private static final com.bct.ngtpa.apiservice.adapter.out.apim.credential.ApimCredentialProfile
      PROFILE =
          new com.bct.ngtpa.apiservice.adapter.out.apim.credential.ApimCredentialProfile(
              "p1",
              "client",
              "secret",
              "api-key",
              "https://token",
              "https://apim",
              List.of(),
              "/certificate");

  private final ApimWebClientFacade facade = mock(ApimWebClientFacade.class);
  private final ApimPayloadCryptoService crypto = mock(ApimPayloadCryptoService.class);
  private final ApimProperties properties = new ApimProperties();
  private DefaultApimExchangeExecutor executor;
  private ApimExchangeRequest<RequestDto, ResponseDto> request;

  private Logger outcomeLoggerLogger;
  private ListAppender<ILoggingEvent> listAppender;

  @BeforeEach
  void setUp() {
    properties.getEncryption().setEnabled(false);
    executor = new DefaultApimExchangeExecutor(facade, crypto, properties);
    request =
        new ApimExchangeRequest<>(
            PATH,
            SOURCE,
            RequestDto.class,
            ResponseDto.class,
            ApimDatabaseScope.ACCOUNT_ENV,
            CONTEXT,
            ApimRetryEligibility.SAFE_BUSINESS_CALL);

    listAppender = new ListAppender<>();
    listAppender.start();
    outcomeLoggerLogger = (Logger) LoggerFactory.getLogger(ApimExchangeOutcomeLogger.class);
    outcomeLoggerLogger.addAppender(listAppender);
  }

  @AfterEach
  void tearDown() {
    outcomeLoggerLogger.detachAppender(listAppender);
    listAppender.stop();
  }

  /**
   * Scenario 1 of 5: {@link ApimWebClientFacade#postSuccessfulExchange} returns {@code
   * Mono.error(...)}. The error signal propagates through {@code postEnvelope(...)} unchanged, and
   * {@code doOnEach(...)} routes the {@code onError} signal to {@code
   * outcomeLogger.logExchangeFailure(...)}, producing exactly one ERROR-level {@code
   * apim.exchange.failed} log line.
   */
  @Test
  void facadeErrorLogsExactlyOneApimExchangeFailedAtError() {
    when(crypto.encryptRequest(PATH, SOURCE, RequestDto.class, null)).thenReturn(SOURCE);
    when(facade.postSuccessfulExchange(
            PATH,
            SOURCE,
            ApimDatabaseScope.ACCOUNT_ENV,
            CONTEXT,
            ApimRetryEligibility.SAFE_BUSINESS_CALL))
        .thenReturn(Mono.error(new IllegalStateException("facade boom")));

    StepVerifier.create(executor.postEnvelope(request))
        .expectErrorSatisfies(e -> assertThat(e).isInstanceOf(IllegalStateException.class))
        .verify();

    assertThat(listAppender.list).hasSize(1);
    ILoggingEvent event = listAppender.list.get(0);
    assertThat(event.getLevel()).isEqualTo(Level.ERROR);
    assertThat(event.getFormattedMessage()).contains("apim.exchange.failed");
    assertThat(event.getFormattedMessage()).contains("IllegalStateException");
  }

  /**
   * Scenario 2 of 5: {@link ApimPayloadCryptoService#decryptResponseEnvelope} throws {@link
   * ApimCryptoException} synchronously inside {@code postPlain(...)}'s {@code Mono.defer(...)}.
   * The exception becomes an {@code onError} signal on the returned {@code Mono}, and {@code
   * doOnEach(...)} routes it to {@code outcomeLogger.logExchangeFailure(...)}, producing exactly
   * one ERROR-level {@code apim.exchange.failed} log line (same event as scenario 1 - this
   * executor does not distinguish crypto failures from transport failures at the logging seam).
   */
  @Test
  void responseDecryptThrowsLogsExactlyOneApimExchangeFailedAtError() {
    when(crypto.encryptRequest(PATH, SOURCE, RequestDto.class, null)).thenReturn(SOURCE);
    when(facade.postSuccessfulExchange(
            PATH,
            SOURCE,
            ApimDatabaseScope.ACCOUNT_ENV,
            CONTEXT,
            ApimRetryEligibility.SAFE_BUSINESS_CALL))
        .thenReturn(Mono.just(new ApimSuccessfulExchange("body", PROFILE, null)));
    when(crypto.decryptResponseEnvelope(PATH, "body", ResponseDto.class, null))
        .thenThrow(new ApimCryptoException("Failed to decrypt APIM response payload."));

    StepVerifier.create(executor.postEnvelope(request))
        .expectErrorSatisfies(e -> assertThat(e).isInstanceOf(ApimCryptoException.class))
        .verify();

    assertThat(listAppender.list).hasSize(1);
    ILoggingEvent event = listAppender.list.get(0);
    assertThat(event.getLevel()).isEqualTo(Level.ERROR);
    assertThat(event.getFormattedMessage()).contains("apim.exchange.failed");
    assertThat(event.getFormattedMessage()).contains("ApimCryptoException");
  }

  /**
   * Scenario 3 of 5: the decrypted envelope has a non-empty {@code err-message}. {@code
   * ApimEnvelopeSchemaClassifier} classifies this as {@code BUSINESS_REJECTED}, and {@code
   * outcomeLogger.logSuccessOutcome(...)} routes it to {@code logBusinessRejected(...)}, producing
   * exactly one WARN-level {@code apim.business.rejected} log line. Mirrors {@code
   * ApimExchangeOutcomeLoggerTest.businessRejectionLogsAtWarnWithSanitizedErrMessage} but exercised
   * end-to-end through the real executor rather than calling the logger directly.
   */
  @Test
  void businessRejectedEnvelopeLogsExactlyOneApimBusinessRejectedAtWarn() {
    when(crypto.encryptRequest(PATH, SOURCE, RequestDto.class, null)).thenReturn(SOURCE);
    when(facade.postSuccessfulExchange(
            PATH,
            SOURCE,
            ApimDatabaseScope.ACCOUNT_ENV,
            CONTEXT,
            ApimRetryEligibility.SAFE_BUSINESS_CALL))
        .thenReturn(Mono.just(new ApimSuccessfulExchange("body", PROFILE, null)));
    ApimResponseEnvelope<ResponseDto> rejectedEnvelope =
        new ApimResponseEnvelope<>(new ApimResponseBody<>("err.tpa.wrong.password", null));
    when(crypto.decryptResponseEnvelope(PATH, "body", ResponseDto.class, null))
        .thenReturn(rejectedEnvelope);

    ApimResponseEnvelope<ResponseDto> result = executor.postEnvelope(request).block();
    assertThat(result).isSameAs(rejectedEnvelope);

    assertThat(listAppender.list).hasSize(1);
    ILoggingEvent event = listAppender.list.get(0);
    assertThat(event.getLevel()).isEqualTo(Level.WARN);
    assertThat(event.getFormattedMessage()).contains("apim.business.rejected");
    assertThat(event.getFormattedMessage()).contains("err.tpa.wrong.password");
  }

  /**
   * Scenario 4 of 5: the decrypted envelope has an empty {@code err-message} but a {@code null}
   * {@code data} field. {@code ApimEnvelopeSchemaClassifier} classifies this as {@code MALFORMED},
   * and {@code outcomeLogger.logSuccessOutcome(...)} routes it to {@code logContractInvalid(...)},
   * producing exactly one ERROR-level {@code apim.contract.invalid} log line. Mirrors {@code
   * ApimExchangeOutcomeLoggerTest.malformedMissingDataLogsContractInvalidAtError} but exercised
   * end-to-end through the real executor.
   */
  @Test
  void malformedMissingDataEnvelopeLogsExactlyOneApimContractInvalidAtError() {
    when(crypto.encryptRequest(PATH, SOURCE, RequestDto.class, null)).thenReturn(SOURCE);
    when(facade.postSuccessfulExchange(
            PATH,
            SOURCE,
            ApimDatabaseScope.ACCOUNT_ENV,
            CONTEXT,
            ApimRetryEligibility.SAFE_BUSINESS_CALL))
        .thenReturn(Mono.just(new ApimSuccessfulExchange("body", PROFILE, null)));
    ApimResponseEnvelope<ResponseDto> malformedEnvelope =
        new ApimResponseEnvelope<>(new ApimResponseBody<>("", null));
    when(crypto.decryptResponseEnvelope(PATH, "body", ResponseDto.class, null))
        .thenReturn(malformedEnvelope);

    ApimResponseEnvelope<ResponseDto> result = executor.postEnvelope(request).block();
    assertThat(result).isSameAs(malformedEnvelope);

    assertThat(listAppender.list).hasSize(1);
    ILoggingEvent event = listAppender.list.get(0);
    assertThat(event.getLevel()).isEqualTo(Level.ERROR);
    assertThat(event.getFormattedMessage()).contains("apim.contract.invalid");
  }

  /**
   * Scenario 5 of 5: the decrypted envelope is well-formed (empty {@code err-message}, non-empty
   * {@code data}). {@code ApimEnvelopeSchemaClassifier} classifies this as {@code SUCCESS}, which
   * {@code logSuccessOutcome(...)} intentionally leaves silent (see {@code
   * ApimExchangeOutcomeLogger}'s class-level Javadoc: a well-formed success is already covered by
   * {@code ApimRequestLoggingExchangeFilter}'s existing outbound request log). No log line at all
   * is produced.
   */
  @Test
  void wellFormedEnvelopeLogsNothing() {
    when(crypto.encryptRequest(PATH, SOURCE, RequestDto.class, null)).thenReturn(SOURCE);
    when(facade.postSuccessfulExchange(
            PATH,
            SOURCE,
            ApimDatabaseScope.ACCOUNT_ENV,
            CONTEXT,
            ApimRetryEligibility.SAFE_BUSINESS_CALL))
        .thenReturn(Mono.just(new ApimSuccessfulExchange("body", PROFILE, null)));
    ApimResponseEnvelope<ResponseDto> wellFormedEnvelope =
        new ApimResponseEnvelope<>(new ApimResponseBody<>("", List.of(new ResponseDto("x"))));
    when(crypto.decryptResponseEnvelope(PATH, "body", ResponseDto.class, null))
        .thenReturn(wellFormedEnvelope);

    ApimResponseEnvelope<ResponseDto> result = executor.postEnvelope(request).block();
    assertThat(result).isSameAs(wellFormedEnvelope);

    assertThat(listAppender.list).isEmpty();
  }

  private record RequestDto(String value) {}

  private record ResponseDto(String value) {}
}
