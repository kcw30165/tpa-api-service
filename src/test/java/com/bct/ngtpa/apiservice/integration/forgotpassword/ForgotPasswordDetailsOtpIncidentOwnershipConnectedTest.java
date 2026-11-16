package com.bct.ngtpa.apiservice.integration.forgotpassword;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.bct.ngtpa.apiservice.adapter.in.web.advice.ApiExceptionHandler;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.forgotpassword.ForgotPasswordController;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.forgotpassword.ForgotPasswordHttpResponses;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.forgotpassword.dto.ForgotPasswordDetailsRequest;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.forgotpassword.mapper.ForgotPasswordCancelWebMapper;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.forgotpassword.mapper.ForgotPasswordCompleteWebMapper;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.forgotpassword.mapper.ForgotPasswordDetailsOtpWebMapper;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.forgotpassword.mapper.ForgotPasswordIdentityWebMapper;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.forgotpassword.mapper.ForgotPasswordStartWebMapper;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.registration.config.AuthFlowCookieWriter;
import com.bct.ngtpa.apiservice.adapter.in.web.error.ErrorEventLogger;
import com.bct.ngtpa.apiservice.adapter.in.web.error.ErrorResponseRenderer;
import com.bct.ngtpa.apiservice.adapter.in.web.error.ExceptionMappingRegistryConfig;
import com.bct.ngtpa.apiservice.adapter.in.web.error.PublicMessageResolver;
import com.bct.ngtpa.apiservice.adapter.in.web.filter.RequestLoggingProperties;
import com.bct.ngtpa.apiservice.adapter.in.web.filter.RequestLoggingWebFilter;
import com.bct.ngtpa.apiservice.adapter.in.web.filter.TerminalIncidentHttpObservation;
import com.bct.ngtpa.apiservice.adapter.in.web.response.ApiStatus;
import com.bct.ngtpa.apiservice.adapter.in.web.response.MutationResponse;
import com.bct.ngtpa.apiservice.adapter.out.apim.ApimPayloadCryptoService;
import com.bct.ngtpa.apiservice.adapter.out.apim.ApimWebClientFacade;
import com.bct.ngtpa.apiservice.adapter.out.apim.config.ApimProperties;
import com.bct.ngtpa.apiservice.adapter.out.apim.credential.ApimCredentialProfile;
import com.bct.ngtpa.apiservice.adapter.out.apim.dto.ApimResponseBody;
import com.bct.ngtpa.apiservice.adapter.out.apim.dto.ApimResponseEnvelope;
import com.bct.ngtpa.apiservice.adapter.out.apim.execution.ApimExchangeOutcomeLogger;
import com.bct.ngtpa.apiservice.adapter.out.apim.execution.DefaultApimExchangeExecutor;
import com.bct.ngtpa.apiservice.adapter.out.apim.forgotpassword.details.ApimForgotPasswordDetailsLookupAdapter;
import com.bct.ngtpa.apiservice.adapter.out.apim.forgotpassword.details.dto.ForgotPasswordTrpSearchMemberRequest;
import com.bct.ngtpa.apiservice.application.dto.auth.forgotpassword.ForgotPasswordResetAuthorization;
import com.bct.ngtpa.apiservice.application.dto.auth.forgotpassword.context.ForgotPasswordAuthFlowContext;
import com.bct.ngtpa.apiservice.application.dto.auth.forgotpassword.context.ForgotPasswordAuthFlowEncryptedContext;
import com.bct.ngtpa.apiservice.application.dto.auth.forgotpassword.context.ForgotPasswordProtectedPhase;
import com.bct.ngtpa.apiservice.application.dto.session.SessionLockHandle;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowScenario;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSession;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionData;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionId;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionSnapshot;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowStatus;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.SessionRevision;
import com.bct.ngtpa.apiservice.application.idempotency.HydratedIdempotentResponse;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotencyExecutor;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotencyFingerprint;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotencyOperationId;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotencyReservationOutcome;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotentExecutionCommand;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotentExecutionResult;
import com.bct.ngtpa.apiservice.application.port.out.AuthFlowSessionMutationLockPort;
import com.bct.ngtpa.apiservice.application.port.out.AuthFlowSessionPort;
import com.bct.ngtpa.apiservice.application.port.out.FailureEventPort;
import com.bct.ngtpa.apiservice.application.port.out.ForgotPasswordAuthFlowContextProtectorPort;
import com.bct.ngtpa.apiservice.application.port.out.ForgotPasswordOtpCommitStatePort;
import com.bct.ngtpa.apiservice.application.port.out.ForgotPasswordOtpSessionBindingPort;
import com.bct.ngtpa.apiservice.application.port.out.MessageDeliveryPort;
import com.bct.ngtpa.apiservice.application.port.out.NoticeTemplateRegistryPort;
import com.bct.ngtpa.apiservice.application.port.out.OtpChallengePort;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.detailsotp.ForgotPasswordDetailsOtpLeasePolicy;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.detailsotp.ForgotPasswordDetailsOtpProtectedOperation;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.detailsotp.ForgotPasswordDetailsOtpResult;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.detailsotp.ForgotPasswordUnderLockDetailsVerifier;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.detailsotp.IdempotentForgotPasswordDetailsOtpService;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.idempotency.ForgotPasswordIdempotencyIntegrationPolicy;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.idempotency.detailsotp.ForgotPasswordDetailsOtpExecutionBridge;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.otp.ForgotPasswordOtpCommitRetentionPolicy;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.otp.SecureForgotPasswordOtpCommitRefGenerator;
import com.bct.ngtpa.apiservice.infrastructure.logging.LoggingSanitizer;
import com.bct.ngtpa.apiservice.infrastructure.logging.StructuredFailureEventLogger;
import com.bct.ngtpa.apiservice.shared.error.ErrorMessageResolver;
import com.bct.ngtpa.apiservice.shared.masking.MaskingProperties;
import com.bct.ngtpa.apiservice.shared.masking.MaskingService;
import com.bct.ngtpa.apiservice.shared.web.RequestCorrelation;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.ConnectException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class ForgotPasswordDetailsOtpIncidentOwnershipConnectedTest {
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
  private static final Instant NOW = Instant.parse("2026-11-14T00:00:00Z");
  private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

  private final ListAppender<ILoggingEvent> appender = new ListAppender<>();
  private Logger failureLogger;
  private Logger apimLogger;
  private Logger requestLogger;
  private Logger exceptionLogger;

  @BeforeEach
  void setUp() {
    appender.start();
    failureLogger = (Logger) LoggerFactory.getLogger(StructuredFailureEventLogger.class);
    apimLogger = (Logger) LoggerFactory.getLogger(ApimExchangeOutcomeLogger.class);
    requestLogger = (Logger) LoggerFactory.getLogger(RequestLoggingWebFilter.class);
    exceptionLogger = (Logger) LoggerFactory.getLogger(ApiExceptionHandler.class);
    failureLogger.addAppender(appender);
    apimLogger.addAppender(appender);
    requestLogger.addAppender(appender);
    exceptionLogger.addAppender(appender);
  }

  @AfterEach
  void tearDown() {
    failureLogger.detachAppender(appender);
    apimLogger.detachAppender(appender);
    requestLogger.detachAppender(appender);
    exceptionLogger.detachAppender(appender);
    appender.stop();
  }

  @Test
  void transportFailureHasOneTerminalErrorAndWarnOnlyObservers() {
    RunResult run = invoke(Scenario.TRANSPORT_FAILURE);

    assertThat(run.response().getStatusCode().value()).isEqualTo(503);
    MutationResponse<?> body = responseBody(run);
    assertThat(body.status()).isEqualTo(ApiStatus.SYSTEM_ERROR);
    assertThat(body.errors())
        .singleElement()
        .satisfies(
            error -> {
              assertThat(error.type()).isEqualTo("SYSTEM");
              assertThat(error.code()).isEqualTo("err.system.unavailable");
              assertThat(error.message()).isNull();
              assertThat(error.targets()).isEmpty();
            });
    assertThat(TerminalIncidentHttpObservation.isOwned(run.exchange())).isTrue();
    assertThat(run.exchange().getResponse().getCookies()).isEmpty();
    assertThat(run.exchange().getResponse().getHeaders().getFirst("X-Request-Id"))
        .isEqualTo("req-fp-details");

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
                      "forgot-password.details-otp.failed",
                      "\"failureOrigin\":\"DOWNSTREAM_TRANSPORT\"",
                      "\"stage\":\"details-lookup\"")
                  .doesNotContain(
                      "APIM-THROWABLE-SECRET",
                      "04/07/1978",
                      "member@example.com",
                      "IDENTITY-SECRET",
                      "CIPHERTEXT-SECRET",
                      "err.tpa.member.not.found");
              assertThat(event.getThrowableProxy()).isNull();
            });
    assertThat(
            appender.list.stream()
                .filter(event -> event.getLoggerName().equals(ApimExchangeOutcomeLogger.class.getName()))
                .map(ILoggingEvent::getLevel))
        .containsExactly(Level.WARN);
    ILoggingEvent completedRequest =
        appender.list.stream()
            .filter(event -> event.getFormattedMessage().contains("\"event\":\"http.request.failed\""))
            .findFirst()
            .orElseThrow();
    assertThat(completedRequest.getLevel()).isEqualTo(Level.WARN);
    assertThat(appender.list.stream().filter(event -> event.getLoggerName().equals(ApiExceptionHandler.class.getName())))
        .isEmpty();
    assertDiagnosticsArePrivate();
  }

  @Test
  void recognizedMemberMismatchPublishesNoIncidentAndDoesNotMarkTheResponse() {
    RunResult run = invoke(Scenario.MEMBER_MISMATCH);

    assertThat(run.response().getStatusCode().value()).isEqualTo(422);
    assertThat(responseBody(run).status()).isEqualTo(ApiStatus.BUSINESS_REJECTED);
    assertThat(responseBody(run).errors())
        .singleElement()
        .satisfies(error -> assertThat(error.code()).isEqualTo("err.forgot-password.member.notFound"));
    assertThat(TerminalIncidentHttpObservation.isOwned(run.exchange())).isFalse();
    assertThat(
            appender.list.stream()
                .filter(event -> event.getLoggerName().equals(StructuredFailureEventLogger.class.getName()))
                .filter(event -> event.getLevel() == Level.ERROR))
        .isEmpty();
    assertThat(
            appender.list.stream()
                .filter(event -> event.getLoggerName().equals(ApimExchangeOutcomeLogger.class.getName()))
                .map(ILoggingEvent::getLevel))
        .containsExactly(Level.WARN);
    assertThat(
            appender.list.stream()
                .filter(event -> event.getFormattedMessage().contains("\"event\":\"http.request.failed\""))
                .map(ILoggingEvent::getLevel))
        .containsExactly(Level.WARN);
    assertDiagnosticsArePrivate();
  }

  @Test
  void localTechnicalExceptionMappingDoesNotDuplicateThePublishedIncident() {
    RunResult run = invoke(Scenario.LOCAL_FAILURE);

    assertThat(run.response().getStatusCode().value()).isEqualTo(500);
    assertThat(responseBody(run).status()).isEqualTo(ApiStatus.SYSTEM_ERROR);
    assertThat(responseBody(run).errors())
        .singleElement()
        .satisfies(error -> assertThat(error.code()).isEqualTo("err.system.unexpected"));
    assertThat(TerminalIncidentHttpObservation.isOwned(run.exchange())).isTrue();
    assertThat(
            appender.list.stream()
                .filter(event -> event.getLevel() == Level.ERROR)
                .filter(
                    event ->
                        event.getLoggerName()
                            .equals(StructuredFailureEventLogger.class.getName())))
        .hasSize(1);
    assertThat(
            appender.list.stream()
                .filter(event -> event.getLoggerName().equals(ApiExceptionHandler.class.getName()))
                .map(ILoggingEvent::getLevel))
        .containsExactly(Level.WARN);
    assertThat(
            appender.list.stream()
                .filter(event -> event.getFormattedMessage().contains("\"event\":\"http.request.failed\""))
                .map(ILoggingEvent::getLevel))
        .containsExactly(Level.WARN);
    assertDiagnosticsArePrivate();
  }

  @Test
  void responseConstructionFailureRemainsVisibleToTheRequestFilter() {
    RunResult run = invoke(Scenario.RESPONSE_CONSTRUCTION_FAILURE);

    assertThat(run.response()).isNull();
    assertThat(TerminalIncidentHttpObservation.isOwned(run.exchange())).isFalse();
    assertThat(
            appender.list.stream()
                .filter(
                    event ->
                        event.getLoggerName()
                            .equals(StructuredFailureEventLogger.class.getName()))
                .filter(event -> event.getLevel() == Level.ERROR))
        .hasSize(1);
    assertThat(
            appender.list.stream()
                .filter(
                    event ->
                        event.getLoggerName()
                            .equals(RequestLoggingWebFilter.class.getName()))
                .filter(event -> event.getFormattedMessage().contains("\"event\":\"http.request.error\""))
                .map(ILoggingEvent::getLevel))
        .containsExactly(Level.ERROR);
    assertDiagnosticsArePrivate();
  }

  @Test
  void missingSessionUsesExpectedMappingWithoutIncidentOwnership() {
    RunResult run = invoke(Scenario.MISSING_SESSION);

    assertThat(run.response().getStatusCode().value()).isEqualTo(500);
    assertThat(responseBody(run).status()).isEqualTo(ApiStatus.SYSTEM_ERROR);
    assertThat(TerminalIncidentHttpObservation.isOwned(run.exchange())).isFalse();
    assertThat(
            appender.list.stream()
                .filter(event -> event.getLoggerName().equals(StructuredFailureEventLogger.class.getName()))
                .filter(event -> event.getLevel() == Level.ERROR))
        .isEmpty();
    assertThat(
            appender.list.stream()
                .filter(event -> event.getLoggerName().equals(ApiExceptionHandler.class.getName()))
                .map(ILoggingEvent::getLevel))
        .containsExactly(Level.WARN);
    assertThat(
            appender.list.stream()
                .filter(event -> event.getFormattedMessage().contains("\"event\":\"http.request.failed\""))
                .map(ILoggingEvent::getLevel))
        .containsExactly(Level.ERROR);
    assertDiagnosticsArePrivate();
  }

  private void assertDiagnosticsArePrivate() {
    for (ILoggingEvent event : appender.list) {
      assertThat(event.getFormattedMessage())
          .doesNotContain(
              "APIM-THROWABLE-SECRET",
              "SESSION-TECHNICAL-SECRET",
              "04/07/1978",
              "member@example.com",
              "IDENTITY-SECRET",
              "CIPHERTEXT-SECRET",
              "reset-token-secret",
              "csrf-secret",
              "idempotency-key-secret");
      assertThat(event.getThrowableProxy()).isNull();
    }
  }

  private RunResult invoke(Scenario scenario) {
    var sessionId = new AuthFlowSessionId("afs-details");
    var sessions = mock(AuthFlowSessionPort.class);
    if (scenario == Scenario.MISSING_SESSION) {
      when(sessions.loadSnapshot(sessionId)).thenReturn(Mono.just(Optional.empty()));
    } else if (scenario == Scenario.LOCAL_FAILURE) {
      when(sessions.loadSnapshot(sessionId))
          .thenReturn(Mono.error(new IllegalStateException("SESSION-TECHNICAL-SECRET")));
    } else {
      when(sessions.loadSnapshot(sessionId))
          .thenReturn(Mono.just(Optional.of(snapshot(sessionId))));
    }

    var apimFacade = mock(ApimWebClientFacade.class);
    var crypto = mock(ApimPayloadCryptoService.class);
    var apimProperties = new ApimProperties();
    apimProperties.getEncryption().setEnabled(false);
    when(crypto.encryptRequest(
            anyString(),
            any(ForgotPasswordTrpSearchMemberRequest.class),
            eq(ForgotPasswordTrpSearchMemberRequest.class),
            isNull()))
        .thenAnswer(invocation -> invocation.getArgument(1));
    if (scenario == Scenario.TRANSPORT_FAILURE
        || scenario == Scenario.RESPONSE_CONSTRUCTION_FAILURE) {
      when(apimFacade.postSuccessfulExchange(
              anyString(),
              any(),
              any(),
              any(),
              any(),
              any(),
              any()))
          .thenReturn(Mono.error(new ConnectException("APIM-THROWABLE-SECRET")));
    } else if (scenario == Scenario.MEMBER_MISMATCH) {
      when(apimFacade.postSuccessfulExchange(
              anyString(),
              any(),
              any(),
              any(),
              any(),
              any(),
              any()))
          .thenReturn(
              Mono.just(new com.bct.ngtpa.apiservice.adapter.out.apim.execution
                  .ApimSuccessfulExchange("response", profile(), null)));
      when(crypto.decryptResponseEnvelope(
              eq(ApimForgotPasswordDetailsLookupAdapter.API_NAME),
              eq("response"),
              eq(com.bct.ngtpa.apiservice.adapter.out.apim.forgotpassword.details.dto
                  .ForgotPasswordTrpSearchMemberDataItem.class),
              isNull()))
          .thenReturn(
              new ApimResponseEnvelope<>(
                  new ApimResponseBody<>("err.tpa.member.not.found", List.of())));
    }
    var exchangeExecutor =
        new DefaultApimExchangeExecutor(
            apimFacade, crypto, apimProperties, new ApimExchangeOutcomeLogger(OBJECT_MAPPER));
    var verifier =
        new ForgotPasswordUnderLockDetailsVerifier(
            new ApimForgotPasswordDetailsLookupAdapter(exchangeExecutor));
    var locks = mock(AuthFlowSessionMutationLockPort.class);
    when(locks.acquire(any(), any()))
        .thenReturn(Mono.just(new SessionLockHandle(true, "lock-key", "lock-token")));
    when(locks.release(any())).thenReturn(Mono.just(true));
    var protector = mock(ForgotPasswordAuthFlowContextProtectorPort.class);
    when(protector.unprotect(any(ForgotPasswordAuthFlowEncryptedContext.class)))
        .thenReturn(context(sessionId));
    var operation =
        new ForgotPasswordDetailsOtpProtectedOperation(
            new SecureForgotPasswordOtpCommitRefGenerator(),
            locks,
            sessions,
            protector,
            verifier,
            mock(OtpChallengePort.class),
            mock(MessageDeliveryPort.class),
            new MaskingService(new MaskingProperties()),
            mock(ForgotPasswordOtpSessionBindingPort.class),
            mock(ForgotPasswordOtpCommitStatePort.class),
            new ForgotPasswordOtpCommitRetentionPolicy(Duration.ofSeconds(60), Duration.ofMinutes(10)),
            new ForgotPasswordDetailsOtpLeasePolicy(60, 10, 10, 3, 10000, 100, 5000, 1000),
            CLOCK,
            Duration.ofMinutes(5),
            mock(NoticeTemplateRegistryPort.class));

    var bridge = mock(ForgotPasswordDetailsOtpExecutionBridge.class);
    var transientResult = new AtomicReference<ForgotPasswordDetailsOtpResult>();
    when(bridge.toTransientResponse(any()))
        .thenAnswer(
            invocation -> {
              ForgotPasswordDetailsOtpResult result = invocation.getArgument(0);
              transientResult.set(result);
              int status =
                  result.status() == ForgotPasswordDetailsOtpResult.Status.SYSTEM_UNAVAILABLE
                      ? 503
                      : result.status() == ForgotPasswordDetailsOtpResult.Status.BUSINESS_REJECTED
                          ? 422
                          : 500;
              return new HydratedIdempotentResponse(status, "application/json", "{}", Map.of());
            });
    when(bridge.fromTransientResponse(any())).thenAnswer(invocation -> transientResult.get());
    var policy = mock(ForgotPasswordIdempotencyIntegrationPolicy.class);
    when(policy.operationId(any(), any(), any())).thenReturn(mock(IdempotencyOperationId.class));
    when(policy.detailsAndOtp(any())).thenReturn(mock(IdempotencyFingerprint.class));
    FailureEventPort failureEvents =
        new StructuredFailureEventLogger(
            sanitizer());
    var service =
        new IdempotentForgotPasswordDetailsOtpService(
            new InvokingIdempotencyExecutor(),
            operation,
            bridge,
            policy,
            Duration.ofMinutes(5),
            failureEvents);
    var controller = controller(service);
    var exchange =
        MockServerWebExchange.from(
            MockServerHttpRequest.post("/api/v1/auth/forgot-password/details")
                .header(RequestCorrelation.REQUEST_ID_HEADER, "req-fp-details")
                .build());
    var response = new AtomicReference<ResponseEntity<?>>();
    var handler = exceptionHandler();
    var filter = new RequestLoggingWebFilter(new RequestLoggingProperties(), sanitizer(), OBJECT_MAPPER);

    Mono<Void> filtered =
        filter.filter(
            exchange,
            webExchange ->
                invokeController(controller, webExchange)
                    .doOnNext(
                        entity -> {
                          response.set(entity);
                          webExchange.getResponse().setStatusCode(entity.getStatusCode());
                        })
                    .then()
                    .onErrorResume(
                        Exception.class,
                        error -> {
                          if (scenario == Scenario.RESPONSE_CONSTRUCTION_FAILURE) {
                            return Mono.error(error);
                          }
                          var mapped = handler.handle(error, webExchange);
                          response.set(mapped);
                          webExchange.getResponse().setStatusCode(mapped.getStatusCode());
                          return Mono.empty();
                        }));
    try (MockedStatic<ForgotPasswordHttpResponses> responseFactory =
        scenario == Scenario.RESPONSE_CONSTRUCTION_FAILURE
            ? mockStatic(ForgotPasswordHttpResponses.class)
            : null) {
      if (responseFactory != null) {
        responseFactory
            .when(() -> ForgotPasswordHttpResponses.toResponse(any()))
            .thenThrow(new IllegalStateException());
      }
      StepVerifier.create(filtered.onErrorComplete()).verifyComplete();
    }
    return new RunResult(response.get(), exchange);
  }

  @SuppressWarnings("unchecked")
  private static Mono<ResponseEntity<?>> invokeController(
      ForgotPasswordController controller, ServerWebExchange exchange) {
    return (Mono<ResponseEntity<?>>)
        (Mono<?>)
            controller.details(
                new ForgotPasswordDetailsRequest("04/07/1978", "member@example.com", null),
                "afs-details",
                "csrf-secret",
                "https://portal.example",
                "https://portal.example/forgot-password",
                "req-fp-details",
                "en",
                "idempotency-key-secret",
                exchange.getResponse(),
                exchange);
  }

  private static ForgotPasswordController controller(
      IdempotentForgotPasswordDetailsOtpService service) {
    return new ForgotPasswordController(
        command -> Mono.error(new AssertionError("start use case must not be called")),
        new ForgotPasswordStartWebMapper(Duration.ofMinutes(5)),
        command -> Mono.error(new AssertionError("identity use case must not be called")),
        new ForgotPasswordIdentityWebMapper(),
        service,
        new ForgotPasswordDetailsOtpWebMapper(),
        command -> Mono.error(new AssertionError("completion use case must not be called")),
        new ForgotPasswordCompleteWebMapper(),
        command -> Mono.error(new AssertionError("cancel use case must not be called")),
        new ForgotPasswordCancelWebMapper(),
        AuthFlowCookieWriter.defaultWriter(),
        null,
        null,
        null,
        null);
  }

  private ApiExceptionHandler exceptionHandler() {
    var mappings = new ExceptionMappingRegistryConfig().exceptionMappingRegistry();
    return new ApiExceptionHandler(
        mappings,
        new PublicMessageResolver((ErrorMessageResolver) (code, locale, env, trust, scheme) -> null),
        new ErrorEventLogger(sanitizer()),
        new ErrorResponseRenderer());
  }

  private static AuthFlowSessionSnapshot snapshot(AuthFlowSessionId sessionId) {
    return new AuthFlowSessionSnapshot(
        new AuthFlowSession(
            sessionId,
            AuthFlowScenario.FORGOT_PASSWORD,
            AuthFlowStatus.IDENTITY_VERIFIED,
            "csrf-hash",
            null,
            new AuthFlowSessionData(
                null,
                null,
                Map.of(
                    "forgotPasswordContextCiphertext", "CIPHERTEXT-SECRET",
                    "forgotPasswordContextKeyVersion", "v1",
                    "forgotPasswordContextAlgorithm", "AES_GCM_V1")),
            NOW.minusSeconds(30),
            NOW.minusSeconds(5),
            NOW.plusSeconds(300)),
        new SessionRevision("0123456789abcdef0123456789abcdef"));
  }

  private static ForgotPasswordAuthFlowContext context(AuthFlowSessionId sessionId) {
    return new ForgotPasswordAuthFlowContext(
        "member-user",
        "IDENTITY-SECRET",
        "HKID",
        true,
        "reset-subject-id",
        null,
        null,
        null,
        new ForgotPasswordResetAuthorization(
            "reset-token-secret",
            sessionId.value(),
            "reset-subject-id",
            NOW,
            NOW.plusSeconds(600),
            null),
        null,
        null,
        null,
        "v1",
        ForgotPasswordProtectedPhase.IDENTITY_VERIFIED);
  }

  private static ApimCredentialProfile profile() {
    return new ApimCredentialProfile(
        "p1",
        "client",
        "secret",
        "api-key",
        "https://token",
        "https://apim",
        List.of(),
        "/certificate");
  }

  private LoggingSanitizer sanitizer() {
    return new LoggingSanitizer(OBJECT_MAPPER, new MaskingService(new MaskingProperties()));
  }

  private static MutationResponse<?> responseBody(RunResult run) {
    assertThat(run.response().getBody()).isInstanceOf(MutationResponse.class);
    return (MutationResponse<?>) run.response().getBody();
  }

  private enum Scenario {
    TRANSPORT_FAILURE,
    RESPONSE_CONSTRUCTION_FAILURE,
    MEMBER_MISMATCH,
    LOCAL_FAILURE,
    MISSING_SESSION
  }

  private record RunResult(ResponseEntity<?> response, MockServerWebExchange exchange) {}

  private static final class InvokingIdempotencyExecutor implements IdempotencyExecutor {
    @Override
    public Mono<IdempotentExecutionResult> execute(IdempotentExecutionCommand command) {
      return command
          .protectedOperation()
          .get()
          .map(
              response ->
                  new IdempotentExecutionResult(IdempotencyReservationOutcome.ACQUIRED, response));
    }

    @Override
    public Mono<Optional<IdempotentExecutionResult>> replayCompleted(
        IdempotencyOperationId operationId,
        IdempotencyFingerprint fingerprint,
        com.bct.ngtpa.apiservice.application.idempotency.ProtectedReplayResolver replayResolver) {
      return Mono.error(new AssertionError("Unexpected replay lookup"));
    }
  }
}
