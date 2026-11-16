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
import com.bct.ngtpa.apiservice.adapter.in.web.auth.forgotpassword.dto.ForgotPasswordCompleteRequest;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.forgotpassword.mapper.ForgotPasswordCompleteWebMapper;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.forgotpassword.mapper.ForgotPasswordCancelWebMapper;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.forgotpassword.mapper.ForgotPasswordDetailsOtpWebMapper;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.forgotpassword.mapper.ForgotPasswordIdentityWebMapper;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.forgotpassword.mapper.ForgotPasswordStartWebMapper;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.registration.config.AuthFlowCookieWriter;
import com.bct.ngtpa.apiservice.adapter.in.web.filter.RequestLoggingProperties;
import com.bct.ngtpa.apiservice.adapter.in.web.filter.RequestLoggingWebFilter;
import com.bct.ngtpa.apiservice.adapter.in.web.filter.TerminalIncidentHttpObservation;
import com.bct.ngtpa.apiservice.adapter.in.web.response.ApiStatus;
import com.bct.ngtpa.apiservice.adapter.in.web.response.MutationResponse;
import com.bct.ngtpa.apiservice.application.dto.observability.FailureOrigin;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowScenario;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSession;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionData;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionId;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionSnapshot;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowStatus;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.SessionRevision;
import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowNextStep;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotencyExecutor;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotencyOperationId;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotencyReservationOutcome;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotentExecutionCommand;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotentExecutionResult;
import com.bct.ngtpa.apiservice.application.port.in.CompleteForgotPasswordUseCase;
import com.bct.ngtpa.apiservice.application.port.out.AuthFlowSessionPort;
import com.bct.ngtpa.apiservice.application.port.out.FailureEventPort;
import com.bct.ngtpa.apiservice.application.port.out.ForgotPasswordAuthFlowContextProtectorPort;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.complete.CompleteForgotPasswordResult;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.complete.CompleteForgotPasswordStatus;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.complete.ForgotPasswordCompleteIncident;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.complete.ForgotPasswordCompleteProtectedExecutionResult;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.complete.ForgotPasswordCompleteProtectedOperation;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.complete.IdempotentCompleteForgotPasswordService;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.idempotency.ForgotPasswordIdempotencyIntegrationPolicy;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.idempotency.complete.ForgotPasswordCompleteExecutionBridge;
import com.bct.ngtpa.apiservice.infrastructure.logging.LoggingSanitizer;
import com.bct.ngtpa.apiservice.infrastructure.logging.StructuredFailureEventLogger;
import com.bct.ngtpa.apiservice.shared.masking.MaskingProperties;
import com.bct.ngtpa.apiservice.shared.masking.MaskingService;
import com.bct.ngtpa.apiservice.shared.web.RequestCorrelation;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class ForgotPasswordCompleteIncidentOwnershipConnectedTest {
  private static final String REQUEST_ID = "req-fp-complete";
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
  void acquiredTechnicalFailureHasOneTerminalErrorAndWarnOnlyHttpObservation() {
    RunResult run = invoke(false);

    assertPublicTechnicalFailureResponse(run);
    assertSingleTerminalIncidentError();
    assertWarnOnlyHttpObservation();
    assertDiagnosticPrivacy();
  }

  private void assertPublicTechnicalFailureResponse(RunResult run) {
    assertThat(run.response().getStatusCode().value()).isEqualTo(503);
    MutationResponse<?> body = responseBody(run);
    assertThat(body.status()).isEqualTo(ApiStatus.SYSTEM_ERROR);
    assertThat(body.errors()).singleElement().satisfies(
        error -> {
          assertThat(error.code()).isEqualTo("err.system.unavailable");
          assertThat(error.type()).isEqualTo("SYSTEM");
          assertThat(error.source()).isEqualTo("SERVER");
        });
    assertThat(body.success()).isFalse();
    assertThat(body.messages()).isEmpty();
    assertThat(OBJECT_MAPPER.valueToTree(body).at("/result/nextStep").asText()).isEqualTo("NONE");
    assertThat(OBJECT_MAPPER.valueToTree(body).toString())
        .doesNotContain("publicationReceipt", "terminalFailure", "reset-secret", "customer-secret");
    assertThat(TerminalIncidentHttpObservation.isOwned(run.exchange())).isTrue();
    assertThat(run.exchange().getResponse().getHeaders().getFirst("X-Request-Id"))
        .isEqualTo(REQUEST_ID);
  }

  private void assertSingleTerminalIncidentError() {
    var terminalErrors = appender.list.stream().filter(event -> event.getLevel() == Level.ERROR).toList();
    assertThat(terminalErrors).singleElement().satisfies(
        event -> {
          assertThat(event.getLoggerName()).isEqualTo(StructuredFailureEventLogger.class.getName());
          assertThat(event.getFormattedMessage())
              .contains("forgot-password.complete.failed", "COMPLETE_APIM_FAILURE", REQUEST_ID)
              .doesNotContain("reset-secret", "customer-secret", "ciphertext-secret");
          assertThat(event.getThrowableProxy()).isNull();
        });
  }

  private void assertWarnOnlyHttpObservation() {
    assertThat(requestEvents("http.request.failed"))
        .singleElement()
        .satisfies(event -> assertThat(event.getLevel()).isEqualTo(Level.WARN));
  }

  private void assertDiagnosticPrivacy() {
    assertThat(appender.list)
        .allSatisfy(
            event -> {
              assertThat(event.getFormattedMessage())
                  .doesNotContain(
                      "reset-secret", "customer-secret", "ciphertext-secret", "csrf-secret");
              assertThat(event.getThrowableProxy()).isNull();
            });
  }

  @Test
  void postAcquisitionFailurePublishesProtectedOperationStage() {
    RunResult run =
        invoke(
              false,
              List.of(
                  ForgotPasswordCompleteIncident.local(
                      "COMPLETE_PROTECTED_OPERATION_FAILURE", "protected-operation")));

    assertThat(run.response().getStatusCode().value()).isEqualTo(503);
    assertThat(TerminalIncidentHttpObservation.isOwned(run.exchange())).isTrue();
    assertThat(appender.list.stream().filter(event -> event.getLevel() == Level.ERROR))
        .singleElement()
        .satisfies(
              event -> {
                assertThat(event.getLoggerName())
                    .isEqualTo(StructuredFailureEventLogger.class.getName());
                assertThat(event.getFormattedMessage())
                    .contains(
                        "COMPLETE_PROTECTED_OPERATION_FAILURE",
                        "\"failureOrigin\":\"LOCAL_SYSTEM\"",
                        "\"stage\":\"protected-operation\"")
                    .doesNotContain("reset-secret", "customer-secret", "ciphertext-secret");
                assertThat(event.getThrowableProxy()).isNull();
              });
  }

  @Test
  void primaryAndBlockedSessionWriteIncidentsRemainSeparateTerminalEvents() {
    RunResult run =
        invoke(
              false,
              List.of(
                  ForgotPasswordCompleteIncident.apim(
                      FailureOrigin.DOWNSTREAM_TRANSPORT, "apim-reset"),
                  ForgotPasswordCompleteIncident.local(
                      "COMPLETE_BLOCKED_SESSION_WRITE_FAILURE", "blocked-session-write")));

    assertThat(run.response().getStatusCode().value()).isEqualTo(503);
    assertThat(TerminalIncidentHttpObservation.isOwned(run.exchange())).isTrue();
    var incidentErrors = appender.list.stream().filter(event -> event.getLevel() == Level.ERROR).toList();
    assertThat(incidentErrors)
        .hasSize(2)
        .extracting(ILoggingEvent::getFormattedMessage)
        .satisfiesExactly(
              primary ->
                  assertThat(primary)
                      .contains("COMPLETE_APIM_FAILURE", "\"stage\":\"apim-reset\""),
              blocked ->
                  assertThat(blocked)
                      .contains(
                          "COMPLETE_BLOCKED_SESSION_WRITE_FAILURE",
                          "\"failureOrigin\":\"LOCAL_SYSTEM\"",
                          "\"stage\":\"blocked-session-write\""));
    assertThat(incidentErrors).allSatisfy(event -> assertThat(event.getThrowableProxy()).isNull());
    assertThat(requestEvents("http.request.failed"))
        .singleElement()
        .satisfies(event -> assertThat(event.getLevel()).isEqualTo(Level.WARN));
  }

  @Test
  void unknownOutcomeReplayHasNoNewPublicationOrOwnershipMarker() {
    RunResult run = invoke(true);

    assertThat(run.response().getStatusCode().value()).isEqualTo(503);
    assertThat(responseBody(run).status()).isEqualTo(ApiStatus.SYSTEM_ERROR);
    assertThat(TerminalIncidentHttpObservation.isOwned(run.exchange())).isFalse();
    assertThat(appender.list)
        .noneMatch(event -> event.getLoggerName().equals(StructuredFailureEventLogger.class.getName()));
    assertThat(requestEvents("http.request.failed"))
        .singleElement()
        .satisfies(event -> assertThat(event.getLevel()).isEqualTo(Level.ERROR));
    verifyNoInteractions(run.operation());
  }

  private RunResult invoke(boolean replay) {
    return invoke(
        replay,
        List.of(
            ForgotPasswordCompleteIncident.apim(
                FailureOrigin.DOWNSTREAM_TRANSPORT, "apim-reset")));
  }

  private RunResult invoke(
      boolean replay, List<ForgotPasswordCompleteIncident> incidents) {
    appender.list.clear();
    var operation = mock(ForgotPasswordCompleteProtectedOperation.class);
    var responses = mock(ForgotPasswordCompleteExecutionBridge.class);
    var technical =
        new CompleteForgotPasswordResult(
            CompleteForgotPasswordStatus.SYSTEM_FAILED,
            WorkflowNextStep.NONE,
            false,
            false,
            List.of(), null);
    if (!replay) {
      when(operation.execute(any()))
          .thenReturn(
              Mono.just(
                  ForgotPasswordCompleteProtectedExecutionResult.technicalFailure(
                      technical,
                      incidents)));
    }

    AuthFlowSessionId sid = new AuthFlowSessionId("session-secret");
    AuthFlowSession session =
        new AuthFlowSession(
            sid,
            AuthFlowScenario.FORGOT_PASSWORD,
            AuthFlowStatus.RESET_AUTHORIZED,
            "csrf-hash",
            null,
            new AuthFlowSessionData(
                "email",
                "j***@example.com",
                Map.of(
                    "forgotPasswordContextCiphertext",
                    "ciphertext-secret",
                    "forgotPasswordContextKeyVersion",
                    "version-secret")),
            Instant.parse("2026-09-14T00:00:00Z"),
            Instant.parse("2026-09-14T00:00:00Z"),
            Instant.parse("2026-09-14T01:00:00Z"));
    var sessions = mock(AuthFlowSessionPort.class);
    when(sessions.loadSnapshot(any()))
        .thenReturn(
            Mono.just(
                Optional.of(new AuthFlowSessionSnapshot(session, new SessionRevision("0".repeat(32))))));
    var protector = mock(ForgotPasswordAuthFlowContextProtectorPort.class);
    when(protector.unprotect(any()))
        .thenReturn(
            new com.bct.ngtpa.apiservice.application.dto.auth.forgotpassword.context
                .ForgotPasswordAuthFlowContext(
                "username-secret",
                "identity-secret",
                "customer-secret",
                true,
                "subject-secret",
                "email",
                "person@example.com",
                "p***@example.com",
                null,
                null,
                null,
                null,
                "v1",
                com.bct.ngtpa.apiservice.application.dto.auth.forgotpassword.context
                    .ForgotPasswordProtectedPhase.PASSWORD_SETUP));

    IdempotencyExecutor executor = new CompletionExecutor(replay);
    var policy = mock(ForgotPasswordIdempotencyIntegrationPolicy.class);
    when(policy.operationId(any(), any(), any())).thenReturn(mock(IdempotencyOperationId.class));
    when(policy.complete())
        .thenReturn(
            mock(com.bct.ngtpa.apiservice.application.idempotency.IdempotencyFingerprint.class));
    FailureEventPort failureEvents =
        new StructuredFailureEventLogger(
            new LoggingSanitizer(
                OBJECT_MAPPER, new MaskingService(new MaskingProperties())));
    CompleteForgotPasswordUseCase service =
        new IdempotentCompleteForgotPasswordService(
            executor, sessions, protector, operation, responses, policy, failureEvents);
    ForgotPasswordController controller = controller(service);
    var exchange =
        MockServerWebExchange.from(
            MockServerHttpRequest.post("/api/v1/auth/forgot-password/complete")
                .header(RequestCorrelation.REQUEST_ID_HEADER, REQUEST_ID)
                .build());
    var response = new AtomicReference<org.springframework.http.ResponseEntity<?>>();
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

  private static ForgotPasswordController controller(CompleteForgotPasswordUseCase useCase) {
    return new ForgotPasswordController(
        null,
        new ForgotPasswordStartWebMapper(Duration.ofMinutes(5)),
        null,
        new ForgotPasswordIdentityWebMapper(),
        null,
        new ForgotPasswordDetailsOtpWebMapper(),
        useCase,
        new ForgotPasswordCompleteWebMapper(),
        null,
        new ForgotPasswordCancelWebMapper(),
        AuthFlowCookieWriter.defaultWriter(),
        null,
        null,
        null,
        null);
  }

  @SuppressWarnings("unchecked")
  private static Mono<org.springframework.http.ResponseEntity<?>> invokeController(
      ForgotPasswordController controller, ServerWebExchange exchange) {
    return (Mono<org.springframework.http.ResponseEntity<?>>)
        (Mono<?>)
            controller.complete(
                new ForgotPasswordCompleteRequest("reset-secret", "reset-secret"),
                "session-secret",
                "csrf-secret",
                "idempotency-secret",
                REQUEST_ID,
                "en",
                "https://portal.example",
                "https://portal.example/forgot-password",
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

  private record RunResult(
      org.springframework.http.ResponseEntity<?> response,
      MockServerWebExchange exchange,
      ForgotPasswordCompleteProtectedOperation operation) {}

  private static final class CompletionExecutor implements IdempotencyExecutor {
    private final boolean replay;

    private CompletionExecutor(boolean replay) {
      this.replay = replay;
    }

    @Override
    public Mono<IdempotentExecutionResult> execute(IdempotentExecutionCommand command) {
      if (replay) {
        return Mono.just(
            new IdempotentExecutionResult(
                IdempotencyReservationOutcome.UNKNOWN_OUTCOME_EXISTING, null));
      }
      return command
          .protectedOperation()
          .get()
          .map(
              response ->
                  new IdempotentExecutionResult(IdempotencyReservationOutcome.ACQUIRED, response))
          .onErrorResume(
              failure ->
                  Mono.just(
                      new IdempotentExecutionResult(
                          IdempotencyReservationOutcome.UNKNOWN_OUTCOME_EXISTING, null)));
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
