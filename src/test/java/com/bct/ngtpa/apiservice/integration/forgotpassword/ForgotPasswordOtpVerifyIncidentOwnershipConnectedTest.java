package com.bct.ngtpa.apiservice.integration.forgotpassword;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.forgotpassword.ForgotPasswordController;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.forgotpassword.dto.ForgotPasswordOtpVerifyRequest;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.forgotpassword.mapper.ForgotPasswordOtpResendWebMapper;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.forgotpassword.mapper.ForgotPasswordOtpVerifyWebMapper;
import com.bct.ngtpa.apiservice.adapter.in.web.filter.RequestLoggingProperties;
import com.bct.ngtpa.apiservice.adapter.in.web.filter.RequestLoggingWebFilter;
import com.bct.ngtpa.apiservice.adapter.in.web.filter.TerminalIncidentHttpObservation;
import com.bct.ngtpa.apiservice.adapter.in.web.response.ApiStatus;
import com.bct.ngtpa.apiservice.adapter.in.web.response.MutationResponse;
import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowNextStep;
import com.bct.ngtpa.apiservice.application.idempotency.HydratedIdempotentResponse;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotencyExecutor;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotencyOperationId;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotencyReservationOutcome;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotentExecutionCommand;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotentExecutionResult;
import com.bct.ngtpa.apiservice.application.port.in.VerifyForgotPasswordOtpUseCase;
import com.bct.ngtpa.apiservice.application.port.out.FailureEventPort;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.idempotency.ForgotPasswordIdempotencyIntegrationPolicy;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.idempotency.otpverify.ForgotPasswordOtpVerifyExecutionBridge;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.otp.verify.ForgotPasswordOtpVerifyIncident;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.otp.verify.ForgotPasswordOtpVerifyProtectedExecutionResult;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.otp.verify.ForgotPasswordOtpVerifyProtectedOperation;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.otp.verify.IdempotentVerifyForgotPasswordOtpService;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.otp.verify.VerifyForgotPasswordOtpCommand;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.otp.verify.VerifyForgotPasswordOtpResult;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.otp.verify.VerifyForgotPasswordOtpStatus;
import com.bct.ngtpa.apiservice.infrastructure.logging.LoggingSanitizer;
import com.bct.ngtpa.apiservice.infrastructure.logging.StructuredFailureEventLogger;
import com.bct.ngtpa.apiservice.shared.masking.MaskingProperties;
import com.bct.ngtpa.apiservice.shared.masking.MaskingService;
import com.bct.ngtpa.apiservice.shared.web.RequestCorrelation;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class ForgotPasswordOtpVerifyIncidentOwnershipConnectedTest {
  private static final String REQUEST_ID = "req-fp-otp-verify";
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  private final ListAppender<ILoggingEvent> appender = new ListAppender<>();
  private Logger failureLogger;
  private Logger requestLogger;

  @BeforeEach
  void attachAppender() {
    appender.start();
    failureLogger = (Logger) LoggerFactory.getLogger(StructuredFailureEventLogger.class);
    requestLogger = (Logger) LoggerFactory.getLogger(RequestLoggingWebFilter.class);
    failureLogger.addAppender(appender);
    requestLogger.addAppender(appender);
  }

  @AfterEach
  void detachAppender() {
    failureLogger.detachAppender(appender);
    requestLogger.detachAppender(appender);
    appender.stop();
  }

  @Test
  void acquiredTechnicalFailureHasOneCauseFreeErrorAndWarnOnlyHttpObserver() throws Exception {
    RunResult run = invoke(Scenario.TECHNICAL_FAILURE);

    assertThat(run.response().getStatusCode().value()).isEqualTo(503);
    MutationResponse<?> body = responseBody(run);
    assertThat(body.status()).isEqualTo(ApiStatus.SYSTEM_ERROR);
    assertThat(body.errors())
        .singleElement()
        .satisfies(
            error -> {
              assertThat(error.code()).isEqualTo("err.system.unavailable");
              assertThat(error.type()).isEqualTo("FORM");
              assertThat(error.targets()).isEmpty();
              assertThat(error.message()).isNull();
              assertThat(error.source()).isEqualTo("BFF");
            });
    assertThat(OBJECT_MAPPER.writeValueAsString(body))
        .doesNotContain("publicationReceipt", "terminalFailure", "otp-secret", "member-secret");
    assertThat(TerminalIncidentHttpObservation.isOwned(run.exchange())).isTrue();
    assertThat(run.exchange().getResponse().getHeaders().getFirst("X-Request-Id"))
        .isEqualTo(REQUEST_ID);

    var terminalErrors =
        appender.list.stream().filter(event -> event.getLevel() == Level.ERROR).toList();
    assertThat(terminalErrors)
        .singleElement()
        .satisfies(
            event -> {
              assertThat(event.getLoggerName())
                  .isEqualTo(StructuredFailureEventLogger.class.getName());
              assertThat(event.getFormattedMessage())
                  .contains(
                      "forgot-password.otp-verify.failed",
                      "OTP_VERIFY_TECHNICAL_FAILURE",
                      "session-read",
                      "\"httpStatus\":503",
                      REQUEST_ID)
                  .doesNotContain("otp-secret", "member-secret", "Throwable");
              assertThat(event.getThrowableProxy()).isNull();
            });
    assertThat(requestEvents("http.request.failed"))
        .singleElement()
        .satisfies(event -> assertThat(event.getLevel()).isEqualTo(Level.WARN));
    assertThat(appender.list)
        .allSatisfy(
            event -> {
              assertThat(event.getFormattedMessage())
                  .doesNotContain("otp-secret", "member-secret", "csrf-secret", "idempotency-secret");
              assertThat(event.getThrowableProxy()).isNull();
            });
    verify(run.operation()).execute(any());
  }

  @Test
  void expectedFourHundredAndReplayFailuresHaveNoTechnicalPublicationOrMarker() {
    for (Scenario scenario :
        List.of(
            Scenario.EXPECTED_REJECTION,
            Scenario.COMPLETED_REPLAY,
            Scenario.FAILED_EXISTING,
            Scenario.UNKNOWN_OUTCOME_EXISTING)) {
      RunResult run = invoke(scenario);

      assertThat(TerminalIncidentHttpObservation.isOwned(run.exchange())).isFalse();
      assertThat(
              appender.list.stream()
                  .filter(
                      event ->
                          event.getLoggerName()
                              .equals(StructuredFailureEventLogger.class.getName())))
          .isEmpty();
      if (scenario == Scenario.EXPECTED_REJECTION) {
        assertThat(run.response().getStatusCode().value()).isEqualTo(400);
        assertThat(responseBody(run).status()).isEqualTo(ApiStatus.VALIDATION_FAILED);
        assertThat(requestEvents("http.request.failed"))
            .singleElement()
            .satisfies(event -> assertThat(event.getLevel()).isEqualTo(Level.WARN));
      } else {
        assertThat(run.response().getStatusCode().value()).isEqualTo(503);
        assertThat(responseBody(run).status()).isEqualTo(ApiStatus.SYSTEM_ERROR);
        assertThat(requestEvents("http.request.failed"))
            .singleElement()
            .satisfies(event -> assertThat(event.getLevel()).isEqualTo(Level.ERROR));
        verifyNoInteractions(run.operation());
      }
    }
  }

  @Test
  void publicationFailureLeavesTheHttpResponseUnownedAndObservable() {
    RunResult run = invoke(Scenario.PUBLICATION_FAILURE);

    assertThat(run.response().getStatusCode().value()).isEqualTo(503);
    assertThat(responseBody(run).status()).isEqualTo(ApiStatus.SYSTEM_ERROR);
    assertThat(TerminalIncidentHttpObservation.isOwned(run.exchange())).isFalse();
    assertThat(
            appender.list.stream()
                .filter(
                    event ->
                        event.getLoggerName().equals(StructuredFailureEventLogger.class.getName()))
                .filter(event -> event.getLevel() == Level.ERROR))
        .isEmpty();
    assertThat(requestEvents("http.request.failed"))
        .singleElement()
        .satisfies(
            event -> {
              assertThat(event.getLevel()).isEqualTo(Level.ERROR);
              assertThat(event.getThrowableProxy()).isNull();
              assertThat(event.getFormattedMessage()).doesNotContain("publication-secret");
            });
  }

  private RunResult invoke(Scenario scenario) {
    appender.list.clear();
    var operation = mock(ForgotPasswordOtpVerifyProtectedOperation.class);
    var expected =
        new VerifyForgotPasswordOtpResult(
            VerifyForgotPasswordOtpStatus.AUTH_FLOW_INVALID, WorkflowNextStep.NONE, null);
    var technical =
        new VerifyForgotPasswordOtpResult(
            VerifyForgotPasswordOtpStatus.SYSTEM_FAILED, WorkflowNextStep.NONE, null);
    IdempotencyReservationOutcome outcome = IdempotencyReservationOutcome.ACQUIRED;
    HydratedIdempotentResponse replayResponse =
        new HydratedIdempotentResponse(503, "application/json", "{}", Map.of());
    if (scenario == Scenario.TECHNICAL_FAILURE || scenario == Scenario.PUBLICATION_FAILURE) {
      when(operation.execute(any()))
          .thenReturn(
              Mono.just(
                  new ForgotPasswordOtpVerifyProtectedExecutionResult(
                      technical, ForgotPasswordOtpVerifyIncident.SESSION_READ)));
    } else if (scenario == Scenario.EXPECTED_REJECTION) {
      when(operation.execute(any()))
          .thenReturn(Mono.just(new ForgotPasswordOtpVerifyProtectedExecutionResult(expected)));
      replayResponse = new HydratedIdempotentResponse(400, "application/json", "{}", Map.of());
    } else if (scenario == Scenario.COMPLETED_REPLAY) {
      outcome = IdempotencyReservationOutcome.COMPLETED_REPLAY;
    } else if (scenario == Scenario.FAILED_EXISTING) {
      outcome = IdempotencyReservationOutcome.FAILED_EXISTING;
    } else if (scenario == Scenario.UNKNOWN_OUTCOME_EXISTING) {
      outcome = IdempotencyReservationOutcome.UNKNOWN_OUTCOME_EXISTING;
    }

    var responseBridge = mock(ForgotPasswordOtpVerifyExecutionBridge.class);
    when(responseBridge.toTransientResponse(any())).thenReturn(replayResponse);
    when(responseBridge.fromTransientResponse(any()))
        .thenReturn(scenario == Scenario.EXPECTED_REJECTION ? expected : technical);
    var policy = mock(ForgotPasswordIdempotencyIntegrationPolicy.class);
    when(policy.operationId(any(), any(), any())).thenReturn(mock(IdempotencyOperationId.class));
    when(policy.otpVerify())
        .thenReturn(
            mock(com.bct.ngtpa.apiservice.application.idempotency.IdempotencyFingerprint.class));
    FailureEventPort failureEvents =
        scenario == Scenario.PUBLICATION_FAILURE
            ? event -> {
              throw new IllegalStateException("publication-secret");
            }
            : failureEventLogger();
    var service =
        new IdempotentVerifyForgotPasswordOtpService(
            new InvokingIdempotencyExecutor(outcome, replayResponse),
            operation,
            responseBridge,
            policy,
            failureEvents);
    var controller = controller(service);
    var exchange =
        MockServerWebExchange.from(
            MockServerHttpRequest.post("/api/v1/auth/forgot-password/otp/verify")
                .header(RequestCorrelation.REQUEST_ID_HEADER, REQUEST_ID)
                .build());
    var response = new AtomicReference<ResponseEntity<?>>();
    var filter =
        new RequestLoggingWebFilter(
            new RequestLoggingProperties(),
            new LoggingSanitizer(
                OBJECT_MAPPER, new MaskingService(new MaskingProperties())),
            OBJECT_MAPPER);

    StepVerifier.create(
            filter.filter(
                exchange,
                webExchange ->
                    invokeController(controller, webExchange)
                        .doOnNext(
                            entity -> {
                              response.set(entity);
                              webExchange.getResponse().setStatusCode(entity.getStatusCode());
                            })
                        .then()))
        .verifyComplete();
    return new RunResult(response.get(), exchange, operation);
  }

  private static ForgotPasswordController controller(VerifyForgotPasswordOtpUseCase verifyUseCase) {
    return new ForgotPasswordController(
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        command -> Mono.error(new AssertionError("resend use case must not be called")),
        new ForgotPasswordOtpResendWebMapper(),
        verifyUseCase,
        new ForgotPasswordOtpVerifyWebMapper());
  }

  @SuppressWarnings("unchecked")
  private static Mono<ResponseEntity<?>> invokeController(
      ForgotPasswordController controller, ServerWebExchange exchange) {
    return (Mono<ResponseEntity<?>>)
        (Mono<?>)
            controller.verify(
                new ForgotPasswordOtpVerifyRequest("otp-secret"),
                "afs-secret",
                "csrf-secret",
                "idempotency-secret",
                REQUEST_ID,
                "en",
                "https://portal.example",
                "https://portal.example/forgot-password",
                exchange.getResponse(),
                exchange);
  }

  private static FailureEventPort failureEventLogger() {
    return new StructuredFailureEventLogger(
        new LoggingSanitizer(
            OBJECT_MAPPER, new MaskingService(new MaskingProperties())));
  }

  private List<ILoggingEvent> requestEvents(String eventName) {
    return appender.list.stream()
        .filter(event -> event.getLoggerName().equals(RequestLoggingWebFilter.class.getName()))
        .filter(event -> event.getFormattedMessage().contains("\"event\":\"" + eventName + "\""))
        .toList();
  }

  private static MutationResponse<?> responseBody(RunResult run) {
    assertThat(run.response().getBody()).isInstanceOf(MutationResponse.class);
    return (MutationResponse<?>) run.response().getBody();
  }

  private enum Scenario {
    TECHNICAL_FAILURE,
    PUBLICATION_FAILURE,
    EXPECTED_REJECTION,
    COMPLETED_REPLAY,
    FAILED_EXISTING,
    UNKNOWN_OUTCOME_EXISTING
  }

  private record RunResult(
      ResponseEntity<?> response,
      MockServerWebExchange exchange,
      ForgotPasswordOtpVerifyProtectedOperation operation) {}

  private static final class InvokingIdempotencyExecutor implements IdempotencyExecutor {
    private final IdempotencyReservationOutcome outcome;
    private final HydratedIdempotentResponse replayResponse;

    private InvokingIdempotencyExecutor(
        IdempotencyReservationOutcome outcome, HydratedIdempotentResponse replayResponse) {
      this.outcome = outcome;
      this.replayResponse = replayResponse;
    }

    @Override
    public Mono<IdempotentExecutionResult> execute(IdempotentExecutionCommand command) {
      if (outcome != IdempotencyReservationOutcome.ACQUIRED) {
        return Mono.just(new IdempotentExecutionResult(outcome, replayResponse));
      }
      return command
          .protectedOperation()
          .get()
          .map(response -> new IdempotentExecutionResult(outcome, response));
    }

    @Override
    public Mono<Optional<IdempotentExecutionResult>> replayCompleted(
        IdempotencyOperationId operationId,
        com.bct.ngtpa.apiservice.application.idempotency.IdempotencyFingerprint fingerprint,
        com.bct.ngtpa.apiservice.application.idempotency.ProtectedReplayResolver replayResolver) {
      return Mono.error(new AssertionError("Unexpected replay lookup"));
    }
  }
}
