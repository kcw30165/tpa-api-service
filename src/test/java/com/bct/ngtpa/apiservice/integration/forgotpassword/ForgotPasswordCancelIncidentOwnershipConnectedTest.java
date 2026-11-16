package com.bct.ngtpa.apiservice.integration.forgotpassword;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.forgotpassword.ForgotPasswordController;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.forgotpassword.mapper.ForgotPasswordCancelWebMapper;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.forgotpassword.mapper.ForgotPasswordCompleteWebMapper;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.forgotpassword.mapper.ForgotPasswordDetailsOtpWebMapper;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.forgotpassword.mapper.ForgotPasswordIdentityWebMapper;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.forgotpassword.mapper.ForgotPasswordStartWebMapper;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.registration.config.AuthFlowCookieProperties;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.registration.config.AuthFlowCookieWriter;
import com.bct.ngtpa.apiservice.adapter.in.web.filter.RequestLoggingProperties;
import com.bct.ngtpa.apiservice.adapter.in.web.filter.RequestLoggingWebFilter;
import com.bct.ngtpa.apiservice.adapter.in.web.filter.TerminalIncidentHttpObservation;
import com.bct.ngtpa.apiservice.adapter.in.web.response.ApiError;
import com.bct.ngtpa.apiservice.adapter.in.web.response.ApiStatus;
import com.bct.ngtpa.apiservice.adapter.in.web.response.MutationResponse;
import com.bct.ngtpa.apiservice.application.dto.session.SessionCancellationResult;
import com.bct.ngtpa.apiservice.application.dto.session.SessionCancellationStatus;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowScenario;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSession;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionData;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionId;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowStatus;
import com.bct.ngtpa.apiservice.application.port.in.CancelForgotPasswordUseCase;
import com.bct.ngtpa.apiservice.application.port.out.AuthFlowSessionCancellationPort;
import com.bct.ngtpa.apiservice.application.port.out.AuthFlowSessionPort;
import com.bct.ngtpa.apiservice.application.port.out.FailureEventPort;
import com.bct.ngtpa.apiservice.application.port.out.OtpChallengePort;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.cancel.CancelForgotPasswordResult;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.cancel.CancelForgotPasswordService;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.cancel.CancelForgotPasswordStatus;
import com.bct.ngtpa.apiservice.application.usecase.session.authflow.AuthFlowSessionCsrfService;
import com.bct.ngtpa.apiservice.infrastructure.logging.LoggingSanitizer;
import com.bct.ngtpa.apiservice.infrastructure.logging.StructuredFailureEventLogger;
import com.bct.ngtpa.apiservice.shared.masking.MaskingProperties;
import com.bct.ngtpa.apiservice.shared.masking.MaskingService;
import com.bct.ngtpa.apiservice.shared.web.RequestCorrelation;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class ForgotPasswordCancelIncidentOwnershipConnectedTest {
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
  private static final Instant NOW = Instant.parse("2026-11-14T00:00:00Z");
  private static final AuthFlowSessionId SESSION_ID = new AuthFlowSessionId("afs_fp_cancel");
  private static final String REQUEST_ID = "req-fp-cancel";
  private static final String CSRF_TOKEN = "csrf-safe";
  private static final String ORIGIN = "https://portal.test";
  private static final List<String> ALLOWED_ORIGINS = List.of(ORIGIN);

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
  void sessionReadFailurePublishesOneTerminalEventAndPreservesThe503Contract() {
    var sessions = mock(AuthFlowSessionPort.class);
    when(sessions.load(SESSION_ID))
        .thenReturn(
            Mono.error(
                new IllegalStateException(
                    "sessionId=afs-secret csrfToken=csrf-secret otpChallengeId=otp-secret")));
    var run = invoke(service(sessions, mock(OtpChallengePort.class), successfulCancellation()));

    assertThat(run.response().getStatusCode().value()).isEqualTo(503);
    assertThat(responseBody(run).status()).isEqualTo(ApiStatus.SYSTEM_ERROR);
    assertThat(responseBody(run).errors())
        .singleElement()
        .satisfies(
            error -> {
              assertThat(error.type()).isEqualTo("SYSTEM");
              assertThat(error.code()).isEqualTo("err.system.unavailable");
              assertThat(error.message()).isNull();
              assertThat(error.targets()).isEmpty();
              assertThat(error.source()).isEqualTo("SERVER");
            });
    assertThat(run.exchange().getResponse().getHeaders().getFirst("X-Request-Id"))
        .isEqualTo(REQUEST_ID);
    assertThat(run.exchange().getResponse().getHeaders().getFirst("Set-Cookie")).isNull();
    assertThat(TerminalIncidentHttpObservation.isOwned(run.exchange())).isTrue();
    assertThat(OBJECT_MAPPER.valueToTree(responseBody(run)).toString())
        .doesNotContain("incidentPublished");
    assertSingleIncident("session-read", 503, "err.system.unavailable");
    assertCompletedRequestObservation(Level.WARN);
  }

  @Test
  void csrfInfrastructureFailurePublishesOneTerminalEvent() {
    var sessions = mock(AuthFlowSessionPort.class);
    when(sessions.load(SESSION_ID))
        .thenReturn(
            Mono.just(Optional.of(session(AuthFlowStatus.OTP_SENT, "otp-secret"))),
            Mono.error(new IllegalStateException("csrf storage failed")));
    var run = invoke(service(sessions, mock(OtpChallengePort.class), successfulCancellation()));

    assertThat(run.response().getStatusCode().value()).isEqualTo(503);
    assertThat(TerminalIncidentHttpObservation.isOwned(run.exchange())).isTrue();
    assertSingleIncident("csrf-validation", 503, "err.system.unavailable");
    assertCompletedRequestObservation(Level.WARN);
  }

  @Test
  void otpInvalidationFailurePublishesOnceAndPreventsSessionCancellation() {
    var sessions = sessionsWithActiveFlow("otp-secret");
    var otp = mock(OtpChallengePort.class);
    when(otp.invalidateChallenge(any()))
        .thenReturn(Mono.error(new IllegalStateException("otpChallengeId=otp-secret")));
    var cancellation = successfulCancellation();
    var run = invoke(service(sessions, otp, cancellation));

    assertThat(run.response().getStatusCode().value()).isEqualTo(503);
    assertThat(TerminalIncidentHttpObservation.isOwned(run.exchange())).isTrue();
    verify(cancellation, never()).cancel(any(), any(), any());
    assertSingleIncident("otp-invalidation", 503, "err.system.unavailable");
    assertCompletedRequestObservation(Level.WARN);
  }

  @Test
  void sessionCancellationFailurePublishesOnceAfterOtpInvalidation() {
    var sessions = sessionsWithActiveFlow("otp-secret");
    var otp = mock(OtpChallengePort.class);
    when(otp.invalidateChallenge(any())).thenReturn(Mono.empty());
    var cancellation = mock(AuthFlowSessionCancellationPort.class);
    when(cancellation.cancel(any(), any(), any()))
        .thenReturn(Mono.error(new IllegalStateException("sessionId=afs-secret")));
    var run = invoke(service(sessions, otp, cancellation));

    assertThat(run.response().getStatusCode().value()).isEqualTo(503);
    assertThat(run.exchange().getResponse().getHeaders().getFirst("Set-Cookie")).isNull();
    assertThat(TerminalIncidentHttpObservation.isOwned(run.exchange())).isTrue();
    verify(otp).invalidateChallenge(any());
    verify(cancellation).cancel(any(), any(), any());
    assertSingleIncident("session-cancel", 503, "err.system.unavailable");
    assertCompletedRequestObservation(Level.WARN);
  }

  @Test
  void synchronousTechnicalFailureKeepsTheExisting403AndPublishesAnIncident() {
    var sessions = mock(AuthFlowSessionPort.class);
    var run =
        invoke(
            service(
                sessions,
                mock(OtpChallengePort.class),
                successfulCancellation(),
                new ThrowingClock()));

    assertThat(run.response().getStatusCode().value()).isEqualTo(403);
    assertThat(responseBody(run).status()).isEqualTo(ApiStatus.VALIDATION_FAILED);
    assertThat(responseBody(run).errors())
        .singleElement()
        .extracting(ApiError::code)
        .isEqualTo("err.forgot-password.csrf.invalid");
    assertThat(responseBody(run).errors())
        .singleElement()
        .satisfies(
            error -> {
              assertThat(error.type()).isEqualTo("FORM");
              assertThat(error.message()).isNull();
              assertThat(error.targets()).isEmpty();
              assertThat(error.source()).isEqualTo("BFF");
            });
    assertThat(TerminalIncidentHttpObservation.isOwned(run.exchange())).isFalse();
    assertSingleIncident("controller-sync", 403, "err.forgot-password.csrf.invalid");
    assertCompletedRequestObservation(Level.WARN);
  }

  @Test
  void synchronousSessionReadExceptionKeepsTheController403Mapping() {
    var sessions = mock(AuthFlowSessionPort.class);
    when(sessions.load(SESSION_ID))
        .thenThrow(new IllegalStateException("synchronous session read failed"));
    var run = invoke(service(sessions, mock(OtpChallengePort.class), successfulCancellation()));

    assertThat(run.response().getStatusCode().value()).isEqualTo(403);
    assertThat(TerminalIncidentHttpObservation.isOwned(run.exchange())).isFalse();
    assertSingleIncident("controller-sync", 403, "err.forgot-password.csrf.invalid");
    assertCompletedRequestObservation(Level.WARN);
  }

  @Test
  void expectedCsrfRejectionPublishesNoTechnicalEvent() {
    var sessions = sessionsWithActiveFlow("otp-secret");
    var run =
        invoke(
            service(sessions, mock(OtpChallengePort.class), successfulCancellation()),
            "wrong-csrf");

    assertThat(run.response().getStatusCode().value()).isEqualTo(403);
    assertThat(responseBody(run).status()).isEqualTo(ApiStatus.VALIDATION_FAILED);
    assertThat(TerminalIncidentHttpObservation.isOwned(run.exchange())).isFalse();
    assertThat(incidentEvents()).isEmpty();
    assertCompletedRequestObservation(Level.WARN);
  }

  @Test
  void missingSessionCleanupPublishesNoTechnicalEvent() {
    var sessions = mock(AuthFlowSessionPort.class);
    when(sessions.load(SESSION_ID)).thenReturn(Mono.just(Optional.empty()));
    var run = invoke(service(sessions, mock(OtpChallengePort.class), successfulCancellation()));

    assertThat(run.response().getStatusCode().value()).isEqualTo(200);
    assertThat(responseBody(run).status()).isEqualTo(ApiStatus.SUCCESS);
    assertThat(TerminalIncidentHttpObservation.isOwned(run.exchange())).isFalse();
    assertThat(run.exchange().getResponse().getCookies().getFirst("auth_flow_sid"))
        .satisfies(cookie -> assertThat(cookie.getValue()).isEmpty());
    assertThat(run.exchange().getResponse().getCookies().getFirst("XSRF-TOKEN")).isNull();
    assertThat(incidentEvents()).isEmpty();
    assertCompletedRequestObservation(Level.INFO);
  }

  @Test
  void successfulCancellationWithoutOtpPublishesNoTechnicalEvent() {
    var sessions = sessionsWithActiveFlow(null);
    var cancellation = successfulCancellation();
    var run = invoke(service(sessions, mock(OtpChallengePort.class), cancellation));

    assertThat(run.response().getStatusCode().value()).isEqualTo(200);
    assertThat(responseBody(run).status()).isEqualTo(ApiStatus.SUCCESS);
    assertThat(run.exchange().getResponse().getCookies().getFirst("auth_flow_sid"))
        .satisfies(cookie -> assertThat(cookie.getValue()).isEmpty());
    assertThat(run.exchange().getResponse().getCookies().getFirst("XSRF-TOKEN")).isNull();
    assertThat(TerminalIncidentHttpObservation.isOwned(run.exchange())).isFalse();
    assertThat(incidentEvents()).isEmpty();
    assertCompletedRequestObservation(Level.INFO);
  }

  @Test
  void successfulCancellationWithOtpInvalidatesOnceWithoutTechnicalEvent() {
    var sessions = sessionsWithActiveFlow("otp-secret");
    var otp = mock(OtpChallengePort.class);
    when(otp.invalidateChallenge(any())).thenReturn(Mono.empty());
    var run = invoke(service(sessions, otp, successfulCancellation()));

    assertThat(run.response().getStatusCode().value()).isEqualTo(200);
    assertThat(responseBody(run).status()).isEqualTo(ApiStatus.SUCCESS);
    assertThat(TerminalIncidentHttpObservation.isOwned(run.exchange())).isFalse();
    verify(otp).invalidateChallenge(any());
    assertThat(incidentEvents()).isEmpty();
    assertCompletedRequestObservation(Level.INFO);
  }

  @Test
  void reactiveControllerFallbackRemainsUnownedAndObservable() {
    CancelForgotPasswordUseCase useCase =
        command -> Mono.error(new IllegalStateException("unowned fallback"));
    var run = invoke(useCase);

    assertThat(run.response().getStatusCode().value()).isEqualTo(503);
    assertThat(responseBody(run).status()).isEqualTo(ApiStatus.SYSTEM_ERROR);
    assertThat(TerminalIncidentHttpObservation.isOwned(run.exchange())).isFalse();
    assertThat(incidentEvents()).isEmpty();
    assertCompletedRequestObservation(Level.ERROR);
  }

  @Test
  void publicationFailureKeepsThe503UnownedAndRequestErrorObservable() {
    var sessions = mock(AuthFlowSessionPort.class);
    when(sessions.load(SESSION_ID))
        .thenReturn(Mono.error(new IllegalStateException("session read")));
    var publicationAttempts = new AtomicInteger();
    FailureEventPort failingPublisher =
        event -> {
          publicationAttempts.incrementAndGet();
          throw new IllegalStateException("publisher secret");
        };
    var run =
        invoke(
            service(
                sessions,
                mock(OtpChallengePort.class),
                successfulCancellation(),
                Clock.fixed(NOW, ZoneOffset.UTC),
                failingPublisher));

    assertThat(run.response().getStatusCode().value()).isEqualTo(503);
    assertThat(TerminalIncidentHttpObservation.isOwned(run.exchange())).isFalse();
    assertThat(publicationAttempts).hasValue(1);
    assertThat(incidentEvents()).isEmpty();
    assertCompletedRequestObservation(Level.ERROR);
  }

  @Test
  void synchronousPublicationFailureKeepsThe403UnownedAndRequestWarningObservable() {
    var publicationAttempts = new AtomicInteger();
    FailureEventPort failingPublisher =
        event -> {
          publicationAttempts.incrementAndGet();
          throw new IllegalStateException("publisher secret");
        };
    var run =
        invoke(
            service(
                mock(AuthFlowSessionPort.class),
                mock(OtpChallengePort.class),
                successfulCancellation(),
                new ThrowingClock(),
                failingPublisher));

    assertThat(run.response().getStatusCode().value()).isEqualTo(403);
    assertThat(responseBody(run).status()).isEqualTo(ApiStatus.VALIDATION_FAILED);
    assertThat(TerminalIncidentHttpObservation.isOwned(run.exchange())).isFalse();
    assertThat(publicationAttempts).hasValue(1);
    assertThat(incidentEvents()).isEmpty();
    assertCompletedRequestObservation(Level.WARN);
  }

  @Test
  void responseConstructionFailureDoesNotMarkAnOwnedRequest() {
    CancelForgotPasswordUseCase useCase =
        command ->
            Mono.just(
                new CancelForgotPasswordResult(
                    CancelForgotPasswordStatus.CANCELLED, true, true, List.of(), true));
    var cookieWriter =
        new AuthFlowCookieWriter(
            new AuthFlowCookieProperties(
                "auth_flow_sid", "/", Duration.ZERO, true, false, "Lax")) {
          @Override
          public void clearAuthFlowCookie(
              org.springframework.http.server.reactive.ServerHttpResponse response) {
            throw new IllegalStateException("cookie response construction failed");
          }
        };
    var run = invoke(useCase, CSRF_TOKEN, cookieWriter);

    assertThat(run.response().getStatusCode().value()).isEqualTo(503);
    assertThat(TerminalIncidentHttpObservation.isOwned(run.exchange())).isFalse();
    assertThat(incidentEvents()).isEmpty();
    assertCompletedRequestObservation(Level.ERROR);
  }

  private void assertSingleIncident(String stage, int httpStatus, String publicCode) {
    assertThat(incidentEvents())
        .singleElement()
        .satisfies(
            event -> {
              String message = event.getFormattedMessage();
              assertThat(event.getLevel()).isEqualTo(Level.ERROR);
              assertThat(event.getThrowableProxy()).isNull();
              assertThat(message)
                  .contains(
                      "forgot-password.cancel.failed",
                      "\"failureOrigin\":\"LOCAL_SYSTEM\"",
                      "\"internalCode\":\"CANCEL_TECHNICAL_FAILURE\"",
                      "\"publicCode\":\"" + publicCode + "\"",
                      "\"httpStatus\":" + httpStatus,
                      "\"retryable\":false",
                      "\"stage\":\"" + stage + "\"",
                      REQUEST_ID)
                  .doesNotContain("afs-secret", "csrf-secret", "otp-secret", "csrf storage failed");
            });
  }

  private void assertCompletedRequestObservation(Level expectedLevel) {
    String eventName = expectedLevel == Level.INFO ? "http.request.end" : "http.request.failed";
    ILoggingEvent completedRequest =
        appender.list.stream()
            .filter(
                event ->
                    event.getFormattedMessage().contains("\"event\":\"" + eventName + "\""))
            .findFirst()
            .orElseThrow();
    assertThat(completedRequest.getLevel()).isEqualTo(expectedLevel);
  }

  private List<ILoggingEvent> incidentEvents() {
    return appender.list.stream()
        .filter(event -> event.getLoggerName().equals(StructuredFailureEventLogger.class.getName()))
        .filter(event -> event.getLevel() == Level.ERROR)
        .toList();
  }

  private RunResult invoke(CancelForgotPasswordUseCase useCase) {
    return invoke(useCase, CSRF_TOKEN);
  }

  private RunResult invoke(CancelForgotPasswordUseCase useCase, String csrfToken) {
    return invoke(useCase, csrfToken, AuthFlowCookieWriter.defaultWriter());
  }

  private RunResult invoke(
      CancelForgotPasswordUseCase useCase, String csrfToken, AuthFlowCookieWriter cookieWriter) {
    var controller =
        new ForgotPasswordController(
            command -> Mono.error(new AssertionError("start use case must not be called")),
            new ForgotPasswordStartWebMapper(Duration.ofMinutes(5)),
            command -> Mono.error(new AssertionError("identity use case must not be called")),
            new ForgotPasswordIdentityWebMapper(),
            command -> Mono.error(new AssertionError("details use case must not be called")),
            new ForgotPasswordDetailsOtpWebMapper(),
            command -> Mono.error(new AssertionError("complete use case must not be called")),
            new ForgotPasswordCompleteWebMapper(),
            useCase,
            new ForgotPasswordCancelWebMapper(),
            cookieWriter,
            null,
            null,
            null,
            null);
    var exchange =
        MockServerWebExchange.from(
            MockServerHttpRequest.post("/api/v1/auth/forgot-password/cancel")
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
                    controller
                        .cancel(
                            SESSION_ID.value(),
                            csrfToken,
                            ORIGIN,
                            ORIGIN + "/forgot-password",
                            REQUEST_ID,
                            "en",
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

  private static CancelForgotPasswordService service(
      AuthFlowSessionPort sessions, OtpChallengePort otp, AuthFlowSessionCancellationPort cancel) {
    return service(sessions, otp, cancel, Clock.fixed(NOW, ZoneOffset.UTC));
  }

  private static CancelForgotPasswordService service(
      AuthFlowSessionPort sessions,
      OtpChallengePort otp,
      AuthFlowSessionCancellationPort cancel,
      Clock clock) {
    return service(sessions, otp, cancel, clock, failureEventLogger());
  }

  private static CancelForgotPasswordService service(
      AuthFlowSessionPort sessions,
      OtpChallengePort otp,
      AuthFlowSessionCancellationPort cancel,
      Clock clock,
      FailureEventPort failureEvents) {
    return new CancelForgotPasswordService(
        sessions,
        new AuthFlowSessionCsrfService(sessions),
        otp,
        cancel,
        clock,
        failureEvents,
        ALLOWED_ORIGINS);
  }

  private static FailureEventPort failureEventLogger() {
    return new StructuredFailureEventLogger(
        new LoggingSanitizer(
            OBJECT_MAPPER, new MaskingService(new MaskingProperties())));
  }

  private static AuthFlowSessionPort sessionsWithActiveFlow(String otpChallengeId) {
    var sessions = mock(AuthFlowSessionPort.class);
    when(sessions.load(SESSION_ID))
        .thenReturn(Mono.just(Optional.of(session(AuthFlowStatus.OTP_SENT, otpChallengeId))));
    return sessions;
  }

  private static AuthFlowSession session(AuthFlowStatus status, String otpChallengeId) {
    return new AuthFlowSession(
        SESSION_ID,
        AuthFlowScenario.FORGOT_PASSWORD,
        status,
        sha256(CSRF_TOKEN),
        otpChallengeId,
        AuthFlowSessionData.empty(),
        NOW.minusSeconds(60),
        NOW,
        NOW.plusSeconds(1800));
  }

  private static AuthFlowSessionCancellationPort successfulCancellation() {
    var cancellation = mock(AuthFlowSessionCancellationPort.class);
    when(cancellation.cancel(any(), any(), any()))
        .thenReturn(Mono.just(new SessionCancellationResult(SessionCancellationStatus.CANCELLED)));
    return cancellation;
  }

  private static String sha256(String value) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256")
                  .digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception exception) {
      throw new IllegalStateException(exception);
    }
  }

  private static MutationResponse<?> responseBody(RunResult run) {
    assertThat(run.response().getBody()).isInstanceOf(MutationResponse.class);
    return (MutationResponse<?>) run.response().getBody();
  }

  private record RunResult(ResponseEntity<?> response, MockServerWebExchange exchange) {}

  private static final class ThrowingClock extends Clock {
    @Override
    public ZoneId getZone() {
      return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
      return this;
    }

    @Override
    public Instant instant() {
      throw new IllegalStateException("local clock failure");
    }
  }
}
