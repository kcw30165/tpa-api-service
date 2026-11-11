package com.bct.ngtpa.apiservice.adapter.in.web.auth.registration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.registration.config.RegistrationIdempotencyProperties;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.registration.dto.RegistrationOtpVerifyRequest;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.registration.mapper.RegistrationOtpResendWebMapper;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.registration.mapper.RegistrationOtpVerifyWebMapper;
import com.bct.ngtpa.apiservice.adapter.in.web.filter.RequestLoggingProperties;
import com.bct.ngtpa.apiservice.adapter.in.web.filter.RequestLoggingWebFilter;
import com.bct.ngtpa.apiservice.application.dto.auth.idempotency.RegistrationIdempotencyStartOutcome;
import com.bct.ngtpa.apiservice.application.dto.auth.registration.context.RegistrationAuthFlowContextAttributeNames;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowScenario;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSession;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionData;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionId;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowStatus;
import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowNextStep;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotencyExecutor;
import com.bct.ngtpa.apiservice.application.port.in.VerifyRegistrationOtpUseCase;
import com.bct.ngtpa.apiservice.application.port.out.AuthFlowSessionPort;
import com.bct.ngtpa.apiservice.application.port.out.FailureEventPort;
import com.bct.ngtpa.apiservice.application.port.out.RegistrationAuthFlowContextProtectorPort;
import com.bct.ngtpa.apiservice.application.port.out.RegistrationEligibleTrustPersistencePort;
import com.bct.ngtpa.apiservice.application.port.out.RegistrationLookupPort;
import com.bct.ngtpa.apiservice.application.port.out.RegistrationOtpPort;
import com.bct.ngtpa.apiservice.application.port.out.RegistrationSessionPort;
import com.bct.ngtpa.apiservice.application.service.auth.registration.context.DefaultRegistrationProtectedAuthFlowContextLoader;
import com.bct.ngtpa.apiservice.application.service.auth.registration.context.RegistrationProtectedAuthFlowContextStatus;
import com.bct.ngtpa.apiservice.application.service.session.SessionIdGenerator;
import com.bct.ngtpa.apiservice.application.service.session.SessionTtlCalculator;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.idempotency.RegistrationSharedFingerprintFactory;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.idempotency.RegistrationSharedIdempotencyIdentityFactory;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.otp.*;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.otp.idempotency.RegistrationOtpVerifyProtectedOperation;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.otp.idempotency.RegistrationOtpVerifyReplayCodec;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.otp.idempotency.RegistrationOtpVerifySharedExecutionBridge;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.otp.idempotency.SharedVerifyRegistrationOtpService;
import com.bct.ngtpa.apiservice.application.usecase.session.authflow.AuthFlowSessionCsrfService;
import com.bct.ngtpa.apiservice.application.usecase.session.authflow.AuthFlowSessionLifecycleService;
import com.bct.ngtpa.apiservice.application.usecase.session.authflow.Sha256CsrfTokenHasher;
import com.bct.ngtpa.apiservice.infrastructure.logging.LoggingSanitizer;
import com.bct.ngtpa.apiservice.infrastructure.logging.StructuredFailureEventLogger;
import com.bct.ngtpa.apiservice.shared.masking.MaskingProperties;
import com.bct.ngtpa.apiservice.shared.masking.MaskingService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.reflect.Method;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpCookie;
import org.springframework.http.MediaType;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.http.server.reactive.MockServerHttpResponse;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.bind.annotation.PostMapping;
import reactor.core.publisher.Mono;

class AuthRegistrationOtpVerifyControllerTest {
  private final ObjectMapper objectMapper = new ObjectMapper();
  private static final String SESSION_ID = "afs_private_session_marker";
  private static final String CSRF_TOKEN = "csrf_private_marker";
  private static final String IDEMPOTENCY_KEY = "idem_private_marker";
  private static final String CHALLENGE_ID = "challenge_private_marker";
  private static final String OTP_CODE = "987654";
  private static final String CIPHERTEXT = "ciphertext_private_marker";
  private static final String THROWABLE_MESSAGE = "private_throwable_marker";
  private static final String REQUEST_ID = "req-otp-diagnostic";

  @Test
  void otpVerifyEndpointExists() throws Exception {
    Method method =
        RegistrationController.class.getDeclaredMethod(
            "verifyOtp",
            RegistrationOtpVerifyRequest.class,
            String.class,
            String.class,
            String.class,
            String.class,
            String.class,
            String.class,
            String.class,
            org.springframework.http.server.reactive.ServerHttpResponse.class);
    PostMapping mapping = method.getAnnotation(PostMapping.class);
    assertThat(mapping).isNotNull();
    assertThat(mapping.path()).contains("/otp/verify");
  }

  @Test
  void requestDtoExcludesInternalFieldsAndRedactsCode() {
    var fields =
        List.of(RegistrationOtpVerifyRequest.class.getRecordComponents()).stream()
            .map(component -> component.getName())
            .toList();
    assertThat(fields).containsExactly("code");
    assertThat(new RegistrationOtpVerifyRequest("123456").toString()).doesNotContain("123456");
  }

  @Test
  void otpVerifyRequiresAuthFlowCookie() throws Exception {
    var useCase = new RecordingUseCase();

    var controller = otpController(useCase);

    String json =
        objectMapper.writeValueAsString(
            controller
                .verifyOtp(
                    new RegistrationOtpVerifyRequest("123456"),
                    null,
                    "csrf",
                    "idem",
                    "req",
                    "en",
                    null,
                    null,
                    new org.springframework.mock.http.server.reactive.MockServerHttpResponse())
                .block());
    assertThat(json).contains("err.registration.authFlow.invalid");
    assertThat(useCase.called).isFalse();
  }

  @Test
  void otpVerifyRequiresXsrfTokenHeader() throws Exception {
    var useCase = new RecordingUseCase();

    var controller = otpController(useCase);

    String json =
        objectMapper.writeValueAsString(
            controller
                .verifyOtp(
                    new RegistrationOtpVerifyRequest("123456"),
                    "afs",
                    null,
                    "idem",
                    "req",
                    "en",
                    null,
                    null,
                    new org.springframework.mock.http.server.reactive.MockServerHttpResponse())
                .block());
    assertThat(json).contains("err.security.authentication.required");
    assertThat(useCase.called).isFalse();
  }

  @Test
  void otpVerifyRequiresIdempotencyKeyAndDoesNotUseRequestIdAsFallback() throws Exception {
    var useCase = new RecordingUseCase();

    var controller = otpController(useCase);
    Logger logger = (Logger) LoggerFactory.getLogger(StructuredFailureEventLogger.class);
    ListAppender<ILoggingEvent> appender = attach(logger);

    try {
      var response =
          controller
              .verifyOtp(
                  new RegistrationOtpVerifyRequest("123456"),
                  SESSION_ID,
                  CSRF_TOKEN,
                  null,
                  "req\r\nid",
                  "en",
                  null,
                  null,
                  new org.springframework.mock.http.server.reactive.MockServerHttpResponse())
              .block();
      String json = objectMapper.writeValueAsString(response);

      assertThat(response.getStatusCode().value()).isEqualTo(400);
      assertThat(json).contains("err.workflow.idempotency.missing");
      assertThat(useCase.called).isFalse();
      assertPreValidationDiagnostic(
          appender, "IDEMPOTENCY_MISSING", SESSION_ID, CSRF_TOKEN, "123456");
    } finally {
      detach(logger, appender);
    }
  }

  @Test
  void otpVerifyRequiresCode() throws Exception {
    var useCase = new RecordingUseCase();

    var controller = otpController(useCase);
    Logger logger = (Logger) LoggerFactory.getLogger(StructuredFailureEventLogger.class);
    ListAppender<ILoggingEvent> appender = attach(logger);

    try {
      var response =
          controller
              .verifyOtp(
                  new RegistrationOtpVerifyRequest(" "),
                  SESSION_ID,
                  CSRF_TOKEN,
                  IDEMPOTENCY_KEY,
                  "req\r\nid",
                  "en",
                  null,
                  null,
                  new org.springframework.mock.http.server.reactive.MockServerHttpResponse())
              .block();
      String json = objectMapper.writeValueAsString(response);

      assertThat(response.getStatusCode().value()).isEqualTo(400);
      assertThat(json).contains("err.registration.otp.invalid");
      assertThat(useCase.called).isFalse();
      assertPreValidationDiagnostic(
          appender, "VALIDATION_FAILED", SESSION_ID, CSRF_TOKEN, IDEMPOTENCY_KEY, " ");
    } finally {
      detach(logger, appender);
    }
  }

  @Test
  void otpVerifyMapsHeadersCookieAndBodyToUseCaseCommand() {
    var useCase = new RecordingUseCase();

    var controller = otpController(useCase);

    controller
        .verifyOtp(
            new RegistrationOtpVerifyRequest("123456"),
            "afs_abc",
            "csrf_abc",
            "idem_abc",
            "req_abc",
            "zh-HK",
            "https://portal.example",
            "https://portal.example/page",
            new org.springframework.mock.http.server.reactive.MockServerHttpResponse())
        .block();

    assertThat(useCase.called).isTrue();
    assertThat(useCase.command.authFlowSessionId().value()).isEqualTo("afs_abc");
    assertThat(useCase.command.csrfToken()).isEqualTo("csrf_abc");
    assertThat(useCase.command.otpCode()).isEqualTo("123456");
    assertThat(useCase.command.idempotencyKey().value()).isEqualTo("idem_abc");
    assertThat(useCase.command.correlationId()).isEqualTo("req_abc");
    assertThat(useCase.command.acceptLanguage()).isEqualTo("zh-HK");
  }

  @Test
  void otpVerifySuccessDoesNotReturnSecrets() throws Exception {

    var controller = otpController(new RecordingUseCase());

    com.fasterxml.jackson.databind.JsonNode json =
        objectMapper.valueToTree(
            controller
                .verifyOtp(
                    new RegistrationOtpVerifyRequest("123456"),
                    "afs_abc",
                    "csrf_abc",
                    "idem_abc",
                    "req_abc",
                    "en",
                    null,
                    null,
                    new org.springframework.mock.http.server.reactive.MockServerHttpResponse())
                .block());
    assertThat(json.at("/body/result/nextStep").asText()).isEqualTo("USERNAME_CHECK");
    assertThat(json.at("/body/result/data/unregisteredTrusts/0/accountEnv").asText())
        .isEqualTo("OI");
    assertThat(json.at("/body/result/pageRef").isMissingNode()).isTrue();
    assertThat(json.toString()).doesNotContain("registration-username-check");
    assertThat(json.toString())
        .doesNotContain(
            "afs_abc",
            "csrf_abc",
            "idem_abc",
            "123456",
            "otpChallengeId",
            "activeOtpChallengeId",
            "member@example.com",
            "3024102",
            "04/04/1982");
  }

  @ParameterizedTest
  @MethodSource("invalidContextStatuses")
  void logsContextRejectionWithUnchangedPublicResponse(
      RegistrationProtectedAuthFlowContextStatus contextStatus, Level expectedLevel)
      throws Exception {
    Logger logger = (Logger) LoggerFactory.getLogger(StructuredFailureEventLogger.class);
    ListAppender<ILoggingEvent> appender = attach(logger);

    try {
      var response =
          otpController(command -> Mono.just(resultWithContextStatus(contextStatus)))
              .verifyOtp(
                  new RegistrationOtpVerifyRequest("123456"),
                  "private-session-marker",
                  "private-csrf-marker",
                  "private-idempotency-marker",
                  "req\r\nid",
                  "en",
                  null,
                  null,
                  new org.springframework.mock.http.server.reactive.MockServerHttpResponse())
              .block();

      assertThat(response.getStatusCode().value()).isEqualTo(400);
      assertThat(response.getBody().status())
          .isEqualTo(com.bct.ngtpa.apiservice.adapter.in.web.response.ApiStatus.VALIDATION_FAILED);
      assertThat(response.getBody().errors())
          .singleElement()
          .satisfies(
              error -> {
                assertThat(error.type()).isEqualTo("FORM");
                assertThat(error.code()).isEqualTo("err.registration.authFlow.invalid");
                assertThat(error.targets()).containsExactly("PAGE");
              });
      var json = objectMapper.valueToTree(response);
      assertThat(json.toString()).contains("err.registration.authFlow.invalid");
      assertThat(json.toString()).doesNotContain(contextStatus.name());
      assertThat(appender.list).hasSize(1);
      ILoggingEvent event = appender.list.getFirst();
      assertThat(event.getLevel()).isEqualTo(expectedLevel);
      assertThat(event.getThrowableProxy()).isNull();
      var logged = objectMapper.readTree(event.getFormattedMessage());
      assertThat(logged.path("event").asText()).isEqualTo("registration.otp_verify.rejected");
      assertThat(logged.path("requestId").asText()).isEqualTo("req??id");
      assertThat(logged.path("internalCode").asText()).isEqualTo(contextStatus.name());
      assertThat(logged.at("/dimensions/reason").asText()).isEqualTo(contextStatus.name());
      assertThat(logged.path("httpStatus").asInt()).isEqualTo(400);
      assertThat(event.getFormattedMessage())
          .doesNotContain(
              "private-session-marker",
              "private-csrf-marker",
              "private-idempotency-marker",
              "123456");
    } finally {
      detach(logger, appender);
    }
  }

  @Test
  void missingProtectedContextHasOneConnectedTerminalErrorAndNoProtectedEffects() throws Exception {
    assertConnectedContextFailure(false, "MISSING_CONTEXT");
  }

  @Test
  void contextDecryptionFailureHasOneConnectedTerminalErrorAndNoProtectedEffects()
      throws Exception {
    assertConnectedContextFailure(true, "CONTEXT_DECRYPTION_FAILED");
  }

  @Test
  void successfulOtpVerifyDoesNotEmitRejectionDiagnostic() {
    Logger logger = (Logger) LoggerFactory.getLogger(StructuredFailureEventLogger.class);
    ListAppender<ILoggingEvent> appender = attach(logger);

    try {
      otpController(new RecordingUseCase())
          .verifyOtp(
              new RegistrationOtpVerifyRequest("123456"),
              "afs_abc",
              "csrf_abc",
              "idem_abc",
              "req_abc",
              "en",
              null,
              null,
              new org.springframework.mock.http.server.reactive.MockServerHttpResponse())
          .block();

      assertThat(rejectionEvents(appender)).isEmpty();
    } finally {
      detach(logger, appender);
    }
  }

  @Test
  void completedReplayDoesNotEmitDuplicateRejectionDiagnostic() {
    Logger logger = (Logger) LoggerFactory.getLogger(StructuredFailureEventLogger.class);
    ListAppender<ILoggingEvent> appender = attach(logger);
    var replay =
        new VerifyRegistrationOtpResult(
            VerifyRegistrationOtpResultStatus.OTP_INVALID,
            WorkflowNextStep.VERIFY_OTP,
            List.of(),
            null,
            List.of(),
            List.of(),
            RegistrationIdempotencyStartOutcome.COMPLETED_REPLAY,
            null,
            null);

    try {
      otpController(command -> Mono.just(replay))
          .verifyOtp(
              new RegistrationOtpVerifyRequest("123456"),
              "afs_abc",
              "csrf_abc",
              "idem_abc",
              "req_abc",
              "en",
              null,
              null,
              new org.springframework.mock.http.server.reactive.MockServerHttpResponse())
          .block();

      assertThat(rejectionEvents(appender)).isEmpty();
    } finally {
      detach(logger, appender);
    }
  }

  @Test
  void unexpectedTechnicalFailureIsStillPropagatedToTerminalExceptionHandler() {
    var failure = new IllegalStateException("private-technical-marker");
    Logger logger = (Logger) LoggerFactory.getLogger(StructuredFailureEventLogger.class);
    ListAppender<ILoggingEvent> appender = attach(logger);

    try {
      assertThatThrownBy(
              () ->
                  otpController(command -> Mono.error(failure))
                      .verifyOtp(
                          new RegistrationOtpVerifyRequest("123456"),
                          "afs_abc",
                          "csrf_abc",
                          "idem_abc",
                          "req_abc",
                          "en",
                          null,
                          null,
                          new org.springframework.mock.http.server.reactive
                              .MockServerHttpResponse())
                      .block())
          .isSameAs(failure);
      assertThat(rejectionEvents(appender)).isEmpty();
    } finally {
      detach(logger, appender);
    }
  }

  private static Stream<Arguments> invalidContextStatuses() {
    return Stream.of(
        Arguments.of(RegistrationProtectedAuthFlowContextStatus.MISSING_SESSION, Level.WARN),
        Arguments.of(
            RegistrationProtectedAuthFlowContextStatus.EXPIRED_OR_TERMINAL_SESSION, Level.WARN),
        Arguments.of(RegistrationProtectedAuthFlowContextStatus.WRONG_SCENARIO, Level.WARN),
        Arguments.of(RegistrationProtectedAuthFlowContextStatus.INVALID_STATUS, Level.WARN),
        Arguments.of(RegistrationProtectedAuthFlowContextStatus.MISSING_ACTIVE_OTP, Level.WARN),
        Arguments.of(RegistrationProtectedAuthFlowContextStatus.MISSING_CONTEXT, Level.ERROR),
        Arguments.of(
            RegistrationProtectedAuthFlowContextStatus.CONTEXT_DECRYPTION_FAILED, Level.ERROR));
  }

  private static VerifyRegistrationOtpResult resultWithContextStatus(
      RegistrationProtectedAuthFlowContextStatus contextStatus) {
    return new VerifyRegistrationOtpResult(
        VerifyRegistrationOtpResultStatus.INVALID_AUTH_FLOW,
        WorkflowNextStep.NONE,
        List.of(),
        null,
        List.of(),
        List.of(),
        RegistrationIdempotencyStartOutcome.STARTED,
        null,
        contextStatus);
  }

  private static List<ILoggingEvent> rejectionEvents(ListAppender<ILoggingEvent> appender) {
    return appender.list.stream()
        .filter(event -> event.getFormattedMessage().contains("registration.otp_verify.rejected"))
        .toList();
  }

  private void assertConnectedContextFailure(boolean contextPresent, String expectedReason)
      throws Exception {
    ConnectedOtpVerifyFixture fixture = connectedOtpVerifyFixture(contextPresent);
    LoggingSanitizer sanitizer = loggingSanitizer();
    Logger rootLogger = (Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
    ListAppender<ILoggingEvent> appender = attach(rootLogger);

    try {
      ConnectedHttpResult result = executeThroughRequestFilter(fixture.useCase(), sanitizer);
      assertThat(result.response().getStatusCode().value()).isEqualTo(400);
      assertThat(result.serverResponse().getStatusCode().value()).isEqualTo(400);
      assertThat(result.serverResponse().getHeaders().getFirst("X-Request-Id"))
          .isEqualTo(REQUEST_ID);
      var body = objectMapper.valueToTree(result.response().getBody());
      assertThat(body.path("status").asText()).isEqualTo("VALIDATION_FAILED");
      assertThat(body.path("success").asBoolean()).isFalse();
      assertThat(body.at("/errors/0/type").asText()).isEqualTo("FORM");
      assertThat(body.at("/errors/0/code").asText()).isEqualTo("err.registration.authFlow.invalid");
      assertThat(body.at("/errors/0/targets/0").asText()).isEqualTo("PAGE");
      assertThat(body.toString()).doesNotContain(expectedReason);

      List<ILoggingEvent> errors =
          appender.list.stream().filter(event -> event.getLevel() == Level.ERROR).toList();
      assertThat(errors).hasSize(1);
      ILoggingEvent error = errors.getFirst();
      assertThat(error.getLoggerName()).isEqualTo(StructuredFailureEventLogger.class.getName());
      assertThat(error.getThrowableProxy()).isNull();
      var diagnostic = objectMapper.readTree(error.getFormattedMessage());
      assertThat(diagnostic.path("event").asText()).isEqualTo("registration.otp_verify.rejected");
      assertThat(diagnostic.path("requestId").asText()).isEqualTo(REQUEST_ID);
      assertThat(diagnostic.path("internalCode").asText()).isEqualTo(expectedReason);
      assertThat(diagnostic.at("/dimensions/reason").asText()).isEqualTo(expectedReason);
      assertThat(diagnostic.path("httpStatus").asInt()).isEqualTo(400);

      List<ILoggingEvent> requestFailures =
          appender.list.stream()
              .filter(
                  event ->
                      event.getFormattedMessage().contains("\"event\":\"http.request.failed\""))
              .toList();
      assertThat(requestFailures).hasSize(1);
      ILoggingEvent requestFailure = requestFailures.getFirst();
      assertThat(requestFailure.getLevel()).isEqualTo(Level.WARN);
      assertThat(requestFailure.getThrowableProxy()).isNull();
      var requestDiagnostic = objectMapper.readTree(requestFailure.getFormattedMessage());
      assertThat(requestDiagnostic.path("requestId").asText()).isEqualTo(REQUEST_ID);
      assertThat(requestDiagnostic.path("status").asInt()).isEqualTo(400);

      assertNoSensitiveLogValues(
          appender.list,
          SESSION_ID,
          CSRF_TOKEN,
          IDEMPOTENCY_KEY,
          CHALLENGE_ID,
          OTP_CODE,
          CIPHERTEXT,
          THROWABLE_MESSAGE,
          "private-key-version",
          "private-masked-destination");
      verifyNoInteractions(
          fixture.idempotencyExecutor(),
          fixture.otpPort(),
          fixture.registrationSessionPort(),
          fixture.lookupPort(),
          fixture.eligibleTrustsPort());
      verify(fixture.authFlowSessionPort(), times(2)).load(fixture.sessionId());
      verifyNoMoreInteractions(fixture.authFlowSessionPort());
      if (contextPresent) {
        verify(fixture.contextProtector(), times(1)).unprotect(any());
      } else {
        verifyNoInteractions(fixture.contextProtector());
      }
    } finally {
      detach(rootLogger, appender);
    }
  }

  private ConnectedOtpVerifyFixture connectedOtpVerifyFixture(boolean contextPresent) {
    Clock clock = Clock.fixed(Instant.parse("2026-09-17T00:00:00Z"), ZoneOffset.UTC);
    Instant now = clock.instant();
    AuthFlowSessionId sessionId = new AuthFlowSessionId(SESSION_ID);
    String csrfHash = new Sha256CsrfTokenHasher().hash(CSRF_TOKEN);
    Map<String, String> attributes =
        contextPresent
            ? Map.of(
                RegistrationAuthFlowContextAttributeNames.CONTEXT_CIPHERTEXT,
                CIPHERTEXT,
                RegistrationAuthFlowContextAttributeNames.CONTEXT_KEY_VERSION,
                "private-key-version",
                RegistrationAuthFlowContextAttributeNames.CONTEXT_ALGORITHM,
                "AES-GCM")
            : Map.of();
    AuthFlowSession session =
        new AuthFlowSession(
            sessionId,
            AuthFlowScenario.REGISTRATION,
            AuthFlowStatus.OTP_SENT,
            csrfHash,
            CHALLENGE_ID,
            new AuthFlowSessionData("EMAIL", "private-masked-destination", attributes),
            now.minusSeconds(1),
            now,
            now.plusSeconds(600));

    AuthFlowSessionPort authFlowSessionPort = mock(AuthFlowSessionPort.class);
    when(authFlowSessionPort.load(sessionId)).thenReturn(Mono.just(Optional.of(session)));
    SessionIdGenerator unusedSessionIdGenerator =
        command -> {
          throw new AssertionError("Session generation is not part of OTP Verify context loading");
        };
    var lifecycleService =
        new AuthFlowSessionLifecycleService(
            authFlowSessionPort, unusedSessionIdGenerator, new SessionTtlCalculator(), clock);
    var csrfService =
        new AuthFlowSessionCsrfService(
            authFlowSessionPort, () -> "unused-csrf", new Sha256CsrfTokenHasher());
    RegistrationAuthFlowContextProtectorPort contextProtector =
        mock(RegistrationAuthFlowContextProtectorPort.class);
    if (contextPresent) {
      when(contextProtector.unprotect(any()))
          .thenThrow(new IllegalStateException(THROWABLE_MESSAGE));
    }
    var contextLoader =
        new DefaultRegistrationProtectedAuthFlowContextLoader(
            lifecycleService, csrfService, contextProtector, clock);

    IdempotencyExecutor idempotencyExecutor = mock(IdempotencyExecutor.class);
    RegistrationOtpPort otpPort = mock(RegistrationOtpPort.class);
    RegistrationSessionPort registrationSessionPort = mock(RegistrationSessionPort.class);
    RegistrationLookupPort lookupPort = mock(RegistrationLookupPort.class);
    RegistrationEligibleTrustPersistencePort eligibleTrustsPort =
        mock(RegistrationEligibleTrustPersistencePort.class);
    var operation =
        new RegistrationOtpVerifyProtectedOperation(
            otpPort, registrationSessionPort, lookupPort, eligibleTrustsPort, clock);
    var bridge =
        new RegistrationOtpVerifySharedExecutionBridge(new RegistrationOtpVerifyReplayCodec());
    var useCase =
        new SharedVerifyRegistrationOtpService(
            contextLoader,
            idempotencyExecutor,
            operation,
            bridge,
            new RegistrationSharedIdempotencyIdentityFactory(),
            new RegistrationSharedFingerprintFactory());

    return new ConnectedOtpVerifyFixture(
        useCase,
        idempotencyExecutor,
        otpPort,
        registrationSessionPort,
        lookupPort,
        eligibleTrustsPort,
        authFlowSessionPort,
        sessionId,
        contextProtector);
  }

  private ConnectedHttpResult executeThroughRequestFilter(
      VerifyRegistrationOtpUseCase useCase, LoggingSanitizer sanitizer) {
    RequestLoggingProperties properties = new RequestLoggingProperties();
    properties.setEnabled(true);
    RegistrationController controller =
        otpController(useCase, new StructuredFailureEventLogger(sanitizer));
    RequestLoggingWebFilter filter =
        new RequestLoggingWebFilter(properties, sanitizer, objectMapper);
    MockServerWebExchange exchange =
        MockServerWebExchange.from(
            MockServerHttpRequest.post("/api/v1/auth/registration/otp/verify")
                .cookie(new HttpCookie("auth_flow_sid", SESSION_ID))
                .header("X-XSRF-TOKEN", CSRF_TOKEN)
                .header("Idempotency-Key", IDEMPOTENCY_KEY)
                .header("X-Request-Id", REQUEST_ID)
                .header("Accept-Language", "en")
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"code\":\"" + OTP_CODE + "\"}"));
    AtomicReference<org.springframework.http.ResponseEntity<?>> capturedResponse =
        new AtomicReference<>();

    filter
        .filter(
            exchange,
            filteredExchange -> {
              var request = filteredExchange.getRequest();
              String sessionId = request.getCookies().getFirst("auth_flow_sid").getValue();
              return controller
                  .verifyOtp(
                      new RegistrationOtpVerifyRequest(OTP_CODE),
                      sessionId,
                      request.getHeaders().getFirst("X-XSRF-TOKEN"),
                      request.getHeaders().getFirst("Idempotency-Key"),
                      request.getHeaders().getFirst("X-Request-Id"),
                      request.getHeaders().getFirst("Accept-Language"),
                      null,
                      null,
                      filteredExchange.getResponse())
                  .flatMap(
                      response -> {
                        capturedResponse.set(response);
                        filteredExchange.getResponse().setStatusCode(response.getStatusCode());
                        filteredExchange
                            .getResponse()
                            .getHeaders()
                            .setContentType(MediaType.APPLICATION_JSON);
                        try {
                          byte[] body = objectMapper.writeValueAsBytes(response.getBody());
                          return filteredExchange
                              .getResponse()
                              .writeWith(
                                  Mono.just(
                                      filteredExchange.getResponse().bufferFactory().wrap(body)));
                        } catch (JsonProcessingException exception) {
                          return Mono.error(exception);
                        }
                      });
            })
        .block();

    return new ConnectedHttpResult(
        capturedResponse.get(), (MockServerHttpResponse) exchange.getResponse());
  }

  private void assertPreValidationDiagnostic(
      ListAppender<ILoggingEvent> appender, String expectedReason, String... sensitiveValues)
      throws Exception {
    List<ILoggingEvent> events = rejectionEvents(appender);
    assertThat(events).hasSize(1);
    ILoggingEvent event = events.getFirst();
    assertThat(event.getLevel()).isEqualTo(Level.WARN);
    assertThat(event.getThrowableProxy()).isNull();
    var diagnostic = objectMapper.readTree(event.getFormattedMessage());
    assertThat(diagnostic.path("event").asText()).isEqualTo("registration.otp_verify.rejected");
    assertThat(diagnostic.path("requestId").asText()).isEqualTo("req??id");
    assertThat(diagnostic.path("internalCode").asText()).isEqualTo(expectedReason);
    assertThat(diagnostic.at("/dimensions/reason").asText()).isEqualTo(expectedReason);
    assertThat(diagnostic.path("httpStatus").asInt()).isEqualTo(400);
    assertNoSensitiveLogValues(appender.list, sensitiveValues);
  }

  private static void assertNoSensitiveLogValues(
      List<ILoggingEvent> events, String... sensitiveValues) {
    for (ILoggingEvent event : events) {
      assertThat(event.getThrowableProxy()).isNull();
      String logged =
          event.getFormattedMessage()
              + Arrays.deepToString(event.getArgumentArray())
              + String.valueOf(event.getMDCPropertyMap());
      assertThat(logged).doesNotContain(sensitiveValues);
    }
  }

  private static ListAppender<ILoggingEvent> attach(Logger logger) {
    ListAppender<ILoggingEvent> appender = new ListAppender<>();
    appender.start();
    logger.addAppender(appender);
    return appender;
  }

  private static void detach(Logger logger, ListAppender<ILoggingEvent> appender) {
    logger.detachAppender(appender);
    appender.stop();
  }

  private static final class RecordingUseCase implements VerifyRegistrationOtpUseCase {
    boolean called;
    VerifyRegistrationOtpCommand command;

    @Override
    public Mono<VerifyRegistrationOtpResult> verify(VerifyRegistrationOtpCommand command) {
      called = true;
      this.command = command;
      return Mono.just(
          new VerifyRegistrationOtpResult(
              VerifyRegistrationOtpResultStatus.VERIFIED,
              WorkflowNextStep.USERNAME_CHECK,
              List.of(new RegistrationUnregisteredTrust("OI", "JPM")),
              null,
              List.of(),
              List.of(),
              RegistrationIdempotencyStartOutcome.STARTED,
              null,
              null));
    }
  }

  private static RegistrationController otpController(VerifyRegistrationOtpUseCase verifyUseCase) {
    return otpController(verifyUseCase, diagnosticLogger());
  }

  private static RegistrationController otpController(
      VerifyRegistrationOtpUseCase verifyUseCase, FailureEventPort failureEvents) {
    RegistrationController controller =
        new RegistrationController(
            null,
            null,
            com.bct.ngtpa.apiservice.adapter.in.web.auth.registration.config.AuthFlowCookieWriter
                .defaultWriter(),
            null,
            null,
            null,
            null,
            command -> Mono.error(new AssertionError("resend use case must not be called")),
            new RegistrationOtpResendWebMapper(null),
            verifyUseCase,
            new RegistrationOtpVerifyWebMapper(
                new com.bct.ngtpa.apiservice.adapter.in.web.workflow
                    .WorkflowMutationResponseMapper()),
            new RegistrationIdempotencyProperties(),
            null,
            null);
    controller.setFailureEventPort(failureEvents);
    return controller;
  }

  private LoggingSanitizer loggingSanitizer() {
    return new LoggingSanitizer(objectMapper, new MaskingService(new MaskingProperties()));
  }

  private static FailureEventPort diagnosticLogger() {
    var sanitizer =
        new LoggingSanitizer(new ObjectMapper(), new MaskingService(new MaskingProperties()));
    return new StructuredFailureEventLogger(sanitizer);
  }

  private record ConnectedOtpVerifyFixture(
      VerifyRegistrationOtpUseCase useCase,
      IdempotencyExecutor idempotencyExecutor,
      RegistrationOtpPort otpPort,
      RegistrationSessionPort registrationSessionPort,
      RegistrationLookupPort lookupPort,
      RegistrationEligibleTrustPersistencePort eligibleTrustsPort,
      AuthFlowSessionPort authFlowSessionPort,
      AuthFlowSessionId sessionId,
      RegistrationAuthFlowContextProtectorPort contextProtector) {}

  private record ConnectedHttpResult(
      org.springframework.http.ResponseEntity<?> response, MockServerHttpResponse serverResponse) {}
}
