package com.bct.ngtpa.apiservice.adapter.in.web.auth.registration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.registration.config.RegistrationIdempotencyProperties;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.registration.dto.RegistrationOtpVerifyRequest;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.registration.mapper.RegistrationOtpResendWebMapper;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.registration.mapper.RegistrationOtpVerifyWebMapper;
import com.bct.ngtpa.apiservice.application.dto.auth.idempotency.RegistrationIdempotencyStartOutcome;
import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowNextStep;
import com.bct.ngtpa.apiservice.application.port.in.VerifyRegistrationOtpUseCase;
import com.bct.ngtpa.apiservice.application.port.out.FailureEventPort;
import com.bct.ngtpa.apiservice.application.service.auth.registration.context
    .RegistrationProtectedAuthFlowContextStatus;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.otp.*;
import com.bct.ngtpa.apiservice.infrastructure.logging.LoggingSanitizer;
import com.bct.ngtpa.apiservice.infrastructure.logging.StructuredFailureEventLogger;
import com.bct.ngtpa.apiservice.shared.masking.MaskingProperties;
import com.bct.ngtpa.apiservice.shared.masking.MaskingService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.reflect.Method;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PostMapping;
import reactor.core.publisher.Mono;

class AuthRegistrationOtpVerifyControllerTest {
  private final ObjectMapper objectMapper = new ObjectMapper();

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

    String json =
        objectMapper.writeValueAsString(
            controller
                .verifyOtp(
                    new RegistrationOtpVerifyRequest("123456"),
                    "afs",
                    "csrf",
                    null,
                    "req",
                    "en",
                    null,
                    null,
                    new org.springframework.mock.http.server.reactive.MockServerHttpResponse())
                .block());
    assertThat(json).contains("err.workflow.idempotency.missing");
    assertThat(useCase.called).isFalse();
  }

  @Test
  void otpVerifyRequiresCode() throws Exception {
    var useCase = new RecordingUseCase();

    var controller = otpController(useCase);

    String json =
        objectMapper.writeValueAsString(
            controller
                .verifyOtp(
                    new RegistrationOtpVerifyRequest(" "),
                    "afs",
                    "csrf",
                    "idem",
                    "req",
                    "en",
                    null,
                    null,
                    new org.springframework.mock.http.server.reactive.MockServerHttpResponse())
                .block());
    assertThat(json).contains("err.registration.otp.invalid");
    assertThat(useCase.called).isFalse();
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
                          new org.springframework.mock.http.server.reactive.MockServerHttpResponse())
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
        .filter(
            event ->
                event.getFormattedMessage().contains("registration.otp_verify.rejected"))
        .toList();
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
              null));
    }
  }

  private static RegistrationController otpController(VerifyRegistrationOtpUseCase verifyUseCase) {
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
            new com.bct.ngtpa.apiservice.adapter.in.web.workflow.WorkflowMutationResponseMapper()),
        new RegistrationIdempotencyProperties(),
        null,
        null);
    controller.setFailureEventPort(diagnosticLogger());
    return controller;
  }

  private static FailureEventPort diagnosticLogger() {
    var sanitizer =
        new LoggingSanitizer(new ObjectMapper(), new MaskingService(new MaskingProperties()));
    return new StructuredFailureEventLogger(sanitizer);
  }
}
