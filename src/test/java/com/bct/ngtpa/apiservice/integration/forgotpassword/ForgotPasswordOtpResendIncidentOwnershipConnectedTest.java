package com.bct.ngtpa.apiservice.integration.forgotpassword;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.forgotpassword.ForgotPasswordController;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.forgotpassword.mapper.ForgotPasswordOtpResendWebMapper;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.forgotpassword.mapper.ForgotPasswordOtpVerifyWebMapper;
import com.bct.ngtpa.apiservice.adapter.in.web.filter.RequestLoggingProperties;
import com.bct.ngtpa.apiservice.adapter.in.web.filter.RequestLoggingWebFilter;
import com.bct.ngtpa.apiservice.adapter.in.web.filter.TerminalIncidentHttpObservation;
import com.bct.ngtpa.apiservice.adapter.in.web.response.ApiStatus;
import com.bct.ngtpa.apiservice.adapter.in.web.response.MutationResponse;
import com.bct.ngtpa.apiservice.application.idempotency.HydratedIdempotentResponse;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotencyExecutor;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotencyOperationId;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotencyReservationOutcome;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotentExecutionCommand;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotentExecutionResult;
import com.bct.ngtpa.apiservice.application.idempotency.ProtectedReplayReference;
import com.bct.ngtpa.apiservice.application.port.out.FailureEventPort;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.idempotency.ForgotPasswordIdempotencyIntegrationPolicy;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.idempotency.otpresend.ForgotPasswordOtpResendExecutionBridge;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.otp.resend.ForgotPasswordOtpResendIncident;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.otp.resend.ForgotPasswordOtpResendProtectedExecutionResult;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.otp.resend.ForgotPasswordOtpResendProtectedOperation;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.otp.resend.IdempotentResendForgotPasswordOtpService;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.otp.resend.ResendForgotPasswordOtpCommand;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.otp.resend.ResendForgotPasswordOtpResult;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.otp.resend.ResendForgotPasswordOtpStatus;
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

class ForgotPasswordOtpResendIncidentOwnershipConnectedTest {
  private static final String REQUEST_ID = "req-forgot-resend";
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
  void deliveryFailureHasOneOwnedTerminalErrorAndPreservesPublicResponse() throws Exception {
    RunResult run = invoke(Scenario.DELIVERY_FAILED);

    assertThat(run.response().getStatusCode().value()).isEqualTo(503);
    MutationResponse<?> body = responseBody(run);
    assertThat(body.status()).isEqualTo(ApiStatus.SYSTEM_ERROR);
    assertThat(body.errors())
        .singleElement()
        .satisfies(error -> assertThat(error.code()).isEqualTo("err.system.unavailable"));
    assertThat(OBJECT_MAPPER.writeValueAsString(body))
        .doesNotContain("publicationReceipt", "terminalFailure", "delivery-secret", "otp-secret");
    assertThat(TerminalIncidentHttpObservation.isOwned(run.exchange())).isTrue();
    assertThat(run.exchange().getResponse().getCookies()).isEmpty();
    assertThat(run.exchange().getResponse().getHeaders().getFirst("X-Request-Id"))
        .isEqualTo(REQUEST_ID);

    var terminalErrors = appender.list.stream().filter(event -> event.getLevel() == Level.ERROR).toList();
    assertThat(terminalErrors)
        .singleElement()
        .satisfies(
            event -> {
              assertThat(event.getLoggerName()).isEqualTo(StructuredFailureEventLogger.class.getName());
              assertThat(event.getFormattedMessage())
                  .contains(
                      "forgot-password.resend.failed",
                      "OTP_RESEND_TECHNICAL_FAILURE",
                      "message-delivery",
                      "\"httpStatus\":503",
                      REQUEST_ID)
                  .doesNotContain(
                      "delivery-secret",
                      "otp-secret",
                      "csrf-secret",
                      "idempotency-secret");
              assertThat(event.getThrowableProxy()).isNull();
            });
    assertThat(appender.list)
        .allSatisfy(
            event -> {
              assertThat(event.getFormattedMessage())
                  .doesNotContain(
                      "delivery-secret",
                      "otp-secret",
                      "csrf-secret",
                      "idempotency-secret",
                      "afs-secret");
              assertThat(event.getThrowableProxy()).isNull();
            });
    assertThat(requestEvents("http.request.failed"))
        .singleElement()
        .satisfies(event -> assertThat(event.getLevel()).isEqualTo(Level.WARN));
  }

  @Test
  void expectedLockContentionKeepsUnownedRequestFilterError() {
    RunResult run = invoke(Scenario.LOCK_CONTENTION);

    assertThat(run.response().getStatusCode().value()).isEqualTo(503);
    assertThat(responseBody(run).status()).isEqualTo(ApiStatus.SYSTEM_ERROR);
    assertThat(TerminalIncidentHttpObservation.isOwned(run.exchange())).isFalse();
    assertThat(
            appender.list.stream()
                .filter(event -> event.getLoggerName().equals(StructuredFailureEventLogger.class.getName()))
                .filter(event -> event.getLevel() == Level.ERROR))
        .isEmpty();
    assertThat(requestEvents("http.request.failed"))
        .singleElement()
        .satisfies(event -> assertThat(event.getLevel()).isEqualTo(Level.ERROR));
  }

  @Test
  void lockAcquisitionFailureHasOneOwnedLocalSystemIncident() {
    RunResult run = invoke(Scenario.LOCK_ACQUISITION_FAILURE);

    assertThat(run.response().getStatusCode().value()).isEqualTo(503);
    assertThat(responseBody(run).status()).isEqualTo(ApiStatus.SYSTEM_ERROR);
    assertThat(TerminalIncidentHttpObservation.isOwned(run.exchange())).isTrue();
    assertThat(
            appender.list.stream()
                .filter(event -> event.getLoggerName().equals(StructuredFailureEventLogger.class.getName()))
                .filter(event -> event.getLevel() == Level.ERROR))
        .singleElement()
        .satisfies(
            event -> {
              assertThat(event.getFormattedMessage())
                  .contains(
                      "OTP_RESEND_TECHNICAL_FAILURE",
                      "lock-acquisition",
                      "\"failureOrigin\":\"LOCAL_SYSTEM\"",
                      "\"httpStatus\":503")
                  .doesNotContain("lock-secret", "csrf-secret", "idempotency-secret");
              assertThat(event.getThrowableProxy()).isNull();
            });
    assertThat(requestEvents("http.request.failed"))
        .singleElement()
        .satisfies(event -> assertThat(event.getLevel()).isEqualTo(Level.WARN));
  }

  @Test
  void successfulMalformedCounterIncidentDoesNotOwnSuccessfulHttpResponse() {
    RunResult run = invoke(Scenario.MALFORMED_COUNTER);

    assertThat(run.response().getStatusCode().value()).isEqualTo(200);
    assertThat(responseBody(run).status()).isEqualTo(ApiStatus.SUCCESS);
    assertThat(TerminalIncidentHttpObservation.isOwned(run.exchange())).isFalse();
    assertThat(appender.list.stream().filter(event -> event.getLevel() == Level.ERROR))
        .singleElement()
        .satisfies(
            event -> {
              assertThat(event.getLoggerName()).isEqualTo(StructuredFailureEventLogger.class.getName());
              assertThat(event.getFormattedMessage())
                  .contains("OTP_RESEND_COUNTER_MALFORMED", "resend-count", "\"httpStatus\":200")
                  .doesNotContain("not-a-number");
              assertThat(event.getThrowableProxy()).isNull();
            });
    assertThat(requestEvents("http.request.end"))
        .singleElement()
        .satisfies(event -> assertThat(event.getLevel()).isEqualTo(Level.INFO));
  }

  @Test
  void completedDeliveryFailureReplayDoesNotPublishOrInheritOwnership() {
    RunResult run = invoke(Scenario.COMPLETED_REPLAY);

    assertThat(run.response().getStatusCode().value()).isEqualTo(503);
    assertThat(responseBody(run).status()).isEqualTo(ApiStatus.SYSTEM_ERROR);
    assertThat(TerminalIncidentHttpObservation.isOwned(run.exchange())).isFalse();
    assertThat(
            appender.list.stream()
                .filter(event -> event.getLoggerName().equals(StructuredFailureEventLogger.class.getName()))
                .filter(event -> event.getLevel() == Level.ERROR))
        .isEmpty();
    assertThat(requestEvents("http.request.failed"))
        .singleElement()
        .satisfies(event -> assertThat(event.getLevel()).isEqualTo(Level.ERROR));
  }

  private RunResult invoke(Scenario scenario) {
    appender.list.clear();
    var operation = mock(ForgotPasswordOtpResendProtectedOperation.class);
    ResendForgotPasswordOtpResult result;
    List<ForgotPasswordOtpResendIncident> incidents;
    ProtectedReplayReference replayReference = null;
    IdempotencyReservationOutcome outcome = IdempotencyReservationOutcome.ACQUIRED;
    if (scenario == Scenario.DELIVERY_FAILED || scenario == Scenario.COMPLETED_REPLAY) {
      result =
          new ResendForgotPasswordOtpResult(
              ResendForgotPasswordOtpStatus.DELIVERY_FAILED, null, null, null, null, null, null);
      incidents =
          scenario == Scenario.DELIVERY_FAILED
              ? List.of(ForgotPasswordOtpResendIncident.MESSAGE_DELIVERY)
              : List.of();
      if (scenario == Scenario.COMPLETED_REPLAY) {
        outcome = IdempotencyReservationOutcome.COMPLETED_REPLAY;
      } else {
        when(operation.execute(any()))
            .thenReturn(
                Mono.just(
                    new ForgotPasswordOtpResendProtectedExecutionResult(
                        result, null, incidents)));
      }
    } else if (scenario == Scenario.MALFORMED_COUNTER) {
      result =
          new ResendForgotPasswordOtpResult(
              ResendForgotPasswordOtpStatus.RESENT, "j***@example.com", 300, 60, 4, null, null);
      replayReference = new ProtectedReplayReference("replay-ref-1234567890abcd");
      incidents = List.of(ForgotPasswordOtpResendIncident.MALFORMED_RESEND_COUNT);
      when(operation.execute(any()))
          .thenReturn(
              Mono.just(
                  new ForgotPasswordOtpResendProtectedExecutionResult(
                      result, replayReference, incidents)));
    } else {
      result =
          new ResendForgotPasswordOtpResult(
              ResendForgotPasswordOtpStatus.SYSTEM_FAILED, null, null, null, null, null, null);
      incidents =
          scenario == Scenario.LOCK_ACQUISITION_FAILURE
              ? List.of(ForgotPasswordOtpResendIncident.LOCK_ACQUISITION)
              : List.of();
      when(operation.execute(any()))
          .thenReturn(
              Mono.just(
                  new ForgotPasswordOtpResendProtectedExecutionResult(result, null, incidents)));
    }

    var responseBridge = mock(ForgotPasswordOtpResendExecutionBridge.class);
    HydratedIdempotentResponse transientResponse =
        new HydratedIdempotentResponse(502, "application/json", "{}", Map.of());
    when(responseBridge.toTransientResponse(any())).thenReturn(transientResponse);
    when(responseBridge.fromTransientResponse(any())).thenReturn(result);
    var policy = mock(ForgotPasswordIdempotencyIntegrationPolicy.class);
    when(policy.operationId(any(), any(), any())).thenReturn(mock(IdempotencyOperationId.class));
    when(policy.otpResend())
        .thenReturn(
            mock(com.bct.ngtpa.apiservice.application.idempotency.IdempotencyFingerprint.class));
    FailureEventPort failureEvents =
        new StructuredFailureEventLogger(
            new LoggingSanitizer(
                OBJECT_MAPPER, new MaskingService(new MaskingProperties())));
    IdempotencyExecutor executor =
        new InvokingIdempotencyExecutor(outcome, transientResponse);
    var service =
        new IdempotentResendForgotPasswordOtpService(
            executor, operation, responseBridge, policy, Duration.ofMinutes(5), failureEvents);
    ForgotPasswordController controller =
        new ForgotPasswordController(
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
            service,
            new ForgotPasswordOtpResendWebMapper(),
            command -> Mono.error(new AssertionError("verify use case must not be called")),
            new ForgotPasswordOtpVerifyWebMapper());
    var exchange =
        MockServerWebExchange.from(
            MockServerHttpRequest.post("/api/v1/auth/forgot-password/otp/resend")
                .header(RequestCorrelation.REQUEST_ID_HEADER, REQUEST_ID)
                .build());
    var response = new java.util.concurrent.atomic.AtomicReference<ResponseEntity<?>>();
    var filter =
        new RequestLoggingWebFilter(
            new RequestLoggingProperties(),
            new LoggingSanitizer(OBJECT_MAPPER, new MaskingService(new MaskingProperties())),
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
    if (scenario == Scenario.COMPLETED_REPLAY) {
      verifyNoInteractions(operation);
    }
    return new RunResult(response.get(), exchange);
  }

  @SuppressWarnings("unchecked")
  private static Mono<ResponseEntity<?>> invokeController(
      ForgotPasswordController controller, ServerWebExchange exchange) {
    return (Mono<ResponseEntity<?>>)
        (Mono<?>)
            controller.resend(
                "afs-secret",
                "csrf-secret",
                "https://portal.example",
                "https://portal.example/forgot-password",
                REQUEST_ID,
                "en",
                "idempotency-secret",
                exchange.getResponse(),
                exchange);
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
    DELIVERY_FAILED,
    LOCK_CONTENTION,
    LOCK_ACQUISITION_FAILURE,
    MALFORMED_COUNTER,
    COMPLETED_REPLAY
  }

  private record RunResult(ResponseEntity<?> response, MockServerWebExchange exchange) {}

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
      if (outcome == IdempotencyReservationOutcome.COMPLETED_REPLAY) {
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
