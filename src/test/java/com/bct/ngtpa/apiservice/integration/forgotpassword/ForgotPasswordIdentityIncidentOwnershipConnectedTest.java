package com.bct.ngtpa.apiservice.integration.forgotpassword;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.forgotpassword.ForgotPasswordController;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.forgotpassword.dto.ForgotPasswordIdentityCheckRequest;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.forgotpassword.mapper.ForgotPasswordCancelWebMapper;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.forgotpassword.mapper.ForgotPasswordCompleteWebMapper;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.forgotpassword.mapper.ForgotPasswordDetailsOtpWebMapper;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.forgotpassword.mapper.ForgotPasswordIdentityWebMapper;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.forgotpassword.mapper.ForgotPasswordStartWebMapper;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.registration.config.AuthFlowCookieWriter;
import com.bct.ngtpa.apiservice.adapter.in.web.filter.RequestLoggingProperties;
import com.bct.ngtpa.apiservice.adapter.in.web.filter.RequestLoggingWebFilter;
import com.bct.ngtpa.apiservice.adapter.in.web.filter.TerminalIncidentHttpObservation;
import com.bct.ngtpa.apiservice.adapter.in.web.response.ApiStatus;
import com.bct.ngtpa.apiservice.adapter.in.web.response.MutationResponse;
import com.bct.ngtpa.apiservice.application.dto.session.SessionLockHandle;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowScenario;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSession;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionData;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionId;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionSnapshot;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowStatus;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.SessionRevision;
import com.bct.ngtpa.apiservice.application.port.in.VerifyForgotPasswordIdentityUseCase;
import com.bct.ngtpa.apiservice.application.port.out.AuthFlowSessionExpiryRefreshPort;
import com.bct.ngtpa.apiservice.application.port.out.AuthFlowSessionMutationLockPort;
import com.bct.ngtpa.apiservice.application.port.out.AuthFlowSessionPort;
import com.bct.ngtpa.apiservice.application.port.out.FailureEventPort;
import com.bct.ngtpa.apiservice.application.port.out.ForgotPasswordAuthFlowContextProtectorPort;
import com.bct.ngtpa.apiservice.application.service.auth.forgotpassword.context.ForgotPasswordProtectedAuthFlowContextLoader;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.identity.VerifyForgotPasswordIdentityService;
import com.bct.ngtpa.apiservice.infrastructure.logging.LoggingSanitizer;
import com.bct.ngtpa.apiservice.infrastructure.logging.StructuredFailureEventLogger;
import com.bct.ngtpa.apiservice.shared.masking.MaskingProperties;
import com.bct.ngtpa.apiservice.shared.masking.MaskingService;
import com.bct.ngtpa.apiservice.shared.web.RequestCorrelation;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
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

class ForgotPasswordIdentityIncidentOwnershipConnectedTest {
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
  private static final Instant NOW = Instant.parse("2026-11-14T00:00:00Z");
  private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

  private final ListAppender<ILoggingEvent> appender = new ListAppender<>();
  private Logger failureLogger;
  private Logger requestLogger;

  @BeforeEach
  void setUp() {
    appender.start();
    failureLogger = (Logger) LoggerFactory.getLogger(StructuredFailureEventLogger.class);
    requestLogger = (Logger) LoggerFactory.getLogger(RequestLoggingWebFilter.class);
    failureLogger.addAppender(appender);
    requestLogger.addAppender(appender);
  }

  @AfterEach
  void tearDown() {
    failureLogger.detachAppender(appender);
    requestLogger.detachAppender(appender);
    appender.stop();
  }

  @Test
  void technicalSessionFailurePublishesOneSharedErrorAndMarksOnlyCompletedRequestObservation() {
    var sessionId = new AuthFlowSessionId("afs-identity");
    var sessions = mock(AuthFlowSessionPort.class);
    when(sessions.loadSnapshot(sessionId))
        .thenReturn(
            Mono.error(
                new IllegalStateException("policyNo=IDENTITY-SECRET identityValue=MEMBER-SECRET")));
    var locks = acquiredLocks();
    var logger = failureEventLogger();
    var service =
        service(
            mock(ForgotPasswordProtectedAuthFlowContextLoader.class),
            mock(ForgotPasswordAuthFlowContextProtectorPort.class),
            sessions,
            locks,
            mock(AuthFlowSessionExpiryRefreshPort.class),
            logger);

    RunResult run = invoke(service, "afs-identity", "csrf-token");

    assertThat(run.response().getStatusCode().value()).isEqualTo(503);
    MutationResponse<?> body = responseBody(run);
    assertThat(body.status()).isEqualTo(ApiStatus.SYSTEM_ERROR);
    assertThat(body.errors())
        .singleElement()
        .satisfies(
            error -> {
              assertThat(error.type()).isEqualTo("FORM");
              assertThat(error.code()).isEqualTo("err.forgot-password.system.unavailable");
              assertThat(error.message()).isNull();
              assertThat(error.targets()).isEmpty();
              assertThat(error.source()).isEqualTo("BFF");
            });
    assertThat(run.exchange().getResponse().getHeaders().getFirst("Set-Cookie")).isNull();
    assertThat(run.exchange().getResponse().getHeaders().getFirst("X-Request-Id"))
        .isEqualTo("req-fp-identity");
    assertThat(TerminalIncidentHttpObservation.isOwned(run.exchange())).isTrue();

    var terminalErrors =
        appender.list.stream()
            .filter(
                event -> event.getLoggerName().equals(StructuredFailureEventLogger.class.getName()))
            .filter(event -> event.getLevel() == Level.ERROR)
            .toList();
    assertThat(terminalErrors)
        .singleElement()
        .satisfies(
            event -> {
              assertThat(event.getFormattedMessage())
                  .contains(
                      "forgot-password.identity-check.failed",
                      "\"failureOrigin\":\"LOCAL_SYSTEM\"",
                      "\"stage\":\"session-read\"")
                  .doesNotContain(
                      "IDENTITY-SECRET", "MEMBER-SECRET", "policyNo=", "identityValue=");
              assertThat(event.getThrowableProxy()).isNull();
            });
    ILoggingEvent completedRequest =
        appender.list.stream()
            .filter(
                event -> event.getFormattedMessage().contains("\"event\":\"http.request.failed\""))
            .findFirst()
            .orElseThrow();
    assertThat(completedRequest.getLevel()).isEqualTo(Level.WARN);
    assertThat(
            appender.list.stream()
                .filter(
                    event ->
                        event.getFormattedMessage().contains("\"event\":\"http.request.error\"")))
        .isEmpty();
  }

  @Test
  void expectedSecurityRejectionKeepsUnowned503AccessErrorWithoutIncidentPublication() {
    var sessionId = new AuthFlowSessionId("afs-identity");
    var sessions = mock(AuthFlowSessionPort.class);
    when(sessions.loadSnapshot(sessionId)).thenReturn(Mono.just(Optional.of(snapshot(sessionId))));
    var loader = mock(ForgotPasswordProtectedAuthFlowContextLoader.class);
    when(loader.load(any()))
        .thenReturn(
            Mono.just(
                com.bct.ngtpa.apiservice.application.service.auth.forgotpassword.context
                    .ForgotPasswordProtectedAuthFlowContextResult.invalid(
                    com.bct.ngtpa.apiservice.application.service.auth.forgotpassword.context
                        .ForgotPasswordProtectedAuthFlowContextStatus.ORIGIN_REFERER_INVALID)));
    var service =
        service(
            loader,
            mock(ForgotPasswordAuthFlowContextProtectorPort.class),
            sessions,
            acquiredLocks(),
            mock(AuthFlowSessionExpiryRefreshPort.class),
            failureEventLogger());

    RunResult run = invoke(service, "afs-identity", "csrf-token");

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
    ILoggingEvent completedRequest =
        appender.list.stream()
            .filter(
                event -> event.getFormattedMessage().contains("\"event\":\"http.request.failed\""))
            .findFirst()
            .orElseThrow();
    assertThat(completedRequest.getLevel()).isEqualTo(Level.ERROR);
  }

  @Test
  void missingCookieSystemFailureIsNotMarkedAsOwned() {
    VerifyForgotPasswordIdentityUseCase useCase =
        command -> {
          throw new AssertionError("missing-cookie request must not invoke the use case");
        };

    RunResult run = invoke(useCase, null, null);

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
    ILoggingEvent completedRequest =
        appender.list.stream()
            .filter(
                event -> event.getFormattedMessage().contains("\"event\":\"http.request.failed\""))
            .findFirst()
            .orElseThrow();
    assertThat(completedRequest.getLevel()).isEqualTo(Level.ERROR);
  }

  private RunResult invoke(
      VerifyForgotPasswordIdentityUseCase useCase, String sessionId, String csrfToken) {
    var controller =
        new ForgotPasswordController(
            command -> Mono.error(new AssertionError("start use case must not be called")),
            new ForgotPasswordStartWebMapper(Duration.ofMinutes(5)),
            useCase,
            new ForgotPasswordIdentityWebMapper(),
            command -> Mono.error(new AssertionError("details use case must not be called")),
            new ForgotPasswordDetailsOtpWebMapper(),
            command -> Mono.error(new AssertionError("complete use case must not be called")),
            new ForgotPasswordCompleteWebMapper(),
            command -> Mono.error(new AssertionError("cancel use case must not be called")),
            new ForgotPasswordCancelWebMapper(),
            AuthFlowCookieWriter.defaultWriter(),
            null,
            null,
            null,
            null);
    var exchange =
        MockServerWebExchange.from(
            MockServerHttpRequest.post("/api/v1/auth/forgot-password/identity-check")
                .header(RequestCorrelation.REQUEST_ID_HEADER, "req-fp-identity")
                .build());
    var response = new AtomicReference<ResponseEntity<?>>();
    var sanitizer =
        new LoggingSanitizer(OBJECT_MAPPER, new MaskingService(new MaskingProperties()));
    var filter =
        new RequestLoggingWebFilter(new RequestLoggingProperties(), sanitizer, OBJECT_MAPPER);

    StepVerifier.create(
            filter.filter(
                exchange,
                webExchange ->
                    invokeCheck(
                            controller,
                            sessionId,
                            csrfToken,
                            webExchange.getResponse(),
                            webExchange)
                        .doOnNext(
                            entity -> {
                              response.set(entity);
                              webExchange.getResponse().setStatusCode(entity.getStatusCode());
                            })
                        .then()))
        .verifyComplete();
    return new RunResult(response.get(), exchange);
  }

  @SuppressWarnings("unchecked")
  private Mono<ResponseEntity<?>> invokeCheck(
      ForgotPasswordController controller,
      String sessionId,
      String csrfToken,
      org.springframework.http.server.reactive.ServerHttpResponse response,
      ServerWebExchange exchange) {
    return (Mono<ResponseEntity<?>>)
        (Mono<?>)
            controller.check(
                new ForgotPasswordIdentityCheckRequest("ID", "A1234567"),
                sessionId,
                csrfToken,
                "https://portal.example",
                "https://portal.example/identity",
                "req-fp-identity",
                "en",
                response,
                exchange);
  }

  private static VerifyForgotPasswordIdentityService service(
      ForgotPasswordProtectedAuthFlowContextLoader loader,
      ForgotPasswordAuthFlowContextProtectorPort protector,
      AuthFlowSessionPort sessions,
      AuthFlowSessionMutationLockPort locks,
      AuthFlowSessionExpiryRefreshPort expiry,
      FailureEventPort failureEvents) {
    return new VerifyForgotPasswordIdentityService(
        loader, protector, sessions, locks, expiry, CLOCK, Duration.ofSeconds(10), failureEvents);
  }

  private static FailureEventPort failureEventLogger() {
    return new StructuredFailureEventLogger(
        new LoggingSanitizer(OBJECT_MAPPER, new MaskingService(new MaskingProperties())));
  }

  private static MutationResponse<?> responseBody(RunResult run) {
    assertThat(run.response().getBody()).isInstanceOf(MutationResponse.class);
    return (MutationResponse<?>) run.response().getBody();
  }

  private static AuthFlowSessionMutationLockPort acquiredLocks() {
    var locks = mock(AuthFlowSessionMutationLockPort.class);
    when(locks.acquire(any(), any()))
        .thenReturn(Mono.just(new SessionLockHandle(true, "lock-key", "lock-token")));
    when(locks.release(any())).thenReturn(Mono.just(true));
    return locks;
  }

  private static AuthFlowSessionSnapshot snapshot(AuthFlowSessionId sessionId) {
    var session =
        new AuthFlowSession(
            sessionId,
            AuthFlowScenario.FORGOT_PASSWORD,
            AuthFlowStatus.STARTED,
            "csrfhash",
            null,
            new AuthFlowSessionData("EMAIL", "m****r@example.com", Map.of()),
            NOW.minusSeconds(120),
            NOW.minusSeconds(60),
            NOW.plusSeconds(1800));
    return new AuthFlowSessionSnapshot(session, new SessionRevision("0".repeat(32)));
  }

  private record RunResult(ResponseEntity<?> response, MockServerWebExchange exchange) {}
}
