package com.bct.ngtpa.apiservice.adapter.in.web.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.adapter.in.web.auth.forgotpassword.ForgotPasswordController;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.forgotpassword.dto.ForgotPasswordOtpVerifyRequest;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.forgotpassword.mapper.ForgotPasswordOtpResendWebMapper;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.forgotpassword.mapper.ForgotPasswordOtpVerifyWebMapper;
import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowNextStep;
import com.bct.ngtpa.apiservice.application.port.in.VerifyForgotPasswordOtpUseCase;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.otp.verify.VerifyForgotPasswordOtpCommand;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.otp.verify.VerifyForgotPasswordOtpResult;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.otp.verify.VerifyForgotPasswordOtpStatus;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.reflect.Method;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpResponse;
import org.springframework.web.bind.annotation.PostMapping;
import reactor.core.publisher.Mono;

class AuthForgotPasswordOtpVerifyControllerTest {
  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  void otpVerifyEndpointExists() throws Exception {
    Method method =
        ForgotPasswordController.class.getDeclaredMethod(
            "verify",
            ForgotPasswordOtpVerifyRequest.class,
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
        List.of(ForgotPasswordOtpVerifyRequest.class.getRecordComponents()).stream()
            .map(component -> component.getName())
            .toList();

    assertThat(fields).containsExactly("code");
    assertThat(new ForgotPasswordOtpVerifyRequest("123456").toString()).doesNotContain("123456");
  }

  @Test
  void otpVerifyRequiresAuthFlowCookie() throws Exception {
    var useCase = new RecordingUseCase();
    var controller = controller(useCase);

    String json =
        objectMapper.writeValueAsString(
            controller
                .verify(
                    new ForgotPasswordOtpVerifyRequest("123456"),
                    null,
                    "csrf",
                    "idem",
                    "req",
                    "en",
                    null,
                    null,
                    new MockServerHttpResponse())
                .block());

    assertThat(json).contains("err.forgot-password.authFlow.invalid");
    assertThat(useCase.called).isFalse();
  }

  @Test
  void otpVerifyRequiresXsrfTokenHeader() throws Exception {
    var useCase = new RecordingUseCase();
    var controller = controller(useCase);

    String json =
        objectMapper.writeValueAsString(
            controller
                .verify(
                    new ForgotPasswordOtpVerifyRequest("123456"),
                    "afs",
                    null,
                    "idem",
                    "req",
                    "en",
                    null,
                    null,
                    new MockServerHttpResponse())
                .block());

    assertThat(json).contains("err.security.authentication.required");
    assertThat(useCase.called).isFalse();
  }

  @Test
  void otpVerifyRequiresIdempotencyKey() throws Exception {
    var useCase = new RecordingUseCase();
    var controller = controller(useCase);

    String json =
        objectMapper.writeValueAsString(
            controller
                .verify(
                    new ForgotPasswordOtpVerifyRequest("123456"),
                    "afs",
                    "csrf",
                    null,
                    "req",
                    "en",
                    null,
                    null,
                    new MockServerHttpResponse())
                .block());

    assertThat(json).contains("err.workflow.idempotency.missing");
    assertThat(useCase.called).isFalse();
  }

  @Test
  void otpVerifyRequiresCode() throws Exception {
    var useCase = new RecordingUseCase();
    var controller = controller(useCase);

    String json =
        objectMapper.writeValueAsString(
            controller
                .verify(
                    new ForgotPasswordOtpVerifyRequest(" "),
                    "afs",
                    "csrf",
                    "idem",
                    "req",
                    "en",
                    null,
                    null,
                    new MockServerHttpResponse())
                .block());

    assertThat(json).contains("err.forgot-password.otp.invalid");
    assertThat(useCase.called).isFalse();
  }

  @Test
  void otpVerifyMapsHeadersCookieAndBodyToUseCaseCommand() {
    var useCase = new RecordingUseCase();
    var controller = controller(useCase);
    var response = new MockServerHttpResponse();

    controller
        .verify(
            new ForgotPasswordOtpVerifyRequest("123456"),
            "afs_abc",
            "csrf_abc",
            "idem_abc",
            "req_abc",
            "zh-HK",
            "https://portal.example",
            "https://portal.example/page",
            response)
        .block();

    assertThat(useCase.called).isTrue();
    assertThat(useCase.command.sessionId().value()).isEqualTo("afs_abc");
    assertThat(useCase.command.csrfToken()).isEqualTo("csrf_abc");
    assertThat(useCase.command.code()).isEqualTo("123456");
    assertThat(useCase.command.idempotencyKey()).isEqualTo("idem_abc");
    assertThat(useCase.command.requestId()).isEqualTo("req_abc");
    assertThat(useCase.command.language()).isEqualTo("zh-HK");
    assertThat(response.getHeaders().getFirst("X-Request-Id")).isEqualTo("req_abc");
  }

  @Test
  void otpVerifySuccessReturnsPasswordSetupWithoutResetToken() {
    var controller = controller(new RecordingUseCase());

    JsonNode json =
        objectMapper.valueToTree(
            controller
                .verify(
                    new ForgotPasswordOtpVerifyRequest("123456"),
                    "afs_abc",
                    "csrf_abc",
                    "idem_abc",
                    "req_abc",
                    "en",
                    null,
                    null,
                    new MockServerHttpResponse())
                .block());

    assertThat(json.at("/body/result/nextStep").asText()).isEqualTo("PASSWORD_SETUP");
    assertThat(json.at("/body/result/data").isObject()).isTrue();
    assertThat(json.at("/body/result/data").size()).isZero();
    assertThat(json.toString())
        .doesNotContain(
            "afs_abc",
            "csrf_abc",
            "idem_abc",
            "123456",
            "resetAuthorization",
            "authorizationId",
            "auth_flow_sid",
            "portal_sid",
            "XSRF-TOKEN",
            "X-XSRF-TOKEN");
  }

  private static final class RecordingUseCase implements VerifyForgotPasswordOtpUseCase {
    boolean called;
    VerifyForgotPasswordOtpCommand command;

    @Override
    public Mono<VerifyForgotPasswordOtpResult> verify(VerifyForgotPasswordOtpCommand command) {
      called = true;
      this.command = command;
      return Mono.just(
          new VerifyForgotPasswordOtpResult(
              VerifyForgotPasswordOtpStatus.VERIFIED, WorkflowNextStep.PASSWORD_SETUP));
    }
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
        null,
        command -> Mono.error(new AssertionError("resend use case must not be called")),
        new ForgotPasswordOtpResendWebMapper(),
        verifyUseCase,
        new ForgotPasswordOtpVerifyWebMapper());
  }
}
