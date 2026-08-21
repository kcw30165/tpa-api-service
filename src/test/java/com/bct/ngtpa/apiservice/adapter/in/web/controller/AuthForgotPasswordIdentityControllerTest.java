package com.bct.ngtpa.apiservice.adapter.in.web.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.adapter.in.web.auth.forgotpassword.ForgotPasswordController;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.forgotpassword.dto.ForgotPasswordIdentityCheckRequest;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.forgotpassword.mapper.ForgotPasswordCancelWebMapper;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.forgotpassword.mapper.ForgotPasswordCompleteWebMapper;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.forgotpassword.mapper.ForgotPasswordDetailsOtpWebMapper;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.forgotpassword.mapper.ForgotPasswordIdentityWebMapper;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.forgotpassword.mapper.ForgotPasswordStartWebMapper;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.registration.config.AuthFlowCookieWriter;
import com.bct.ngtpa.apiservice.application.port.in.VerifyForgotPasswordIdentityUseCase;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.identity.VerifyForgotPasswordIdentityCommand;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.identity.VerifyForgotPasswordIdentityResult;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.identity.VerifyForgotPasswordIdentityStatus;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.reflect.Method;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpResponse;
import org.springframework.web.bind.annotation.PostMapping;
import reactor.core.publisher.Mono;

class AuthForgotPasswordIdentityControllerTest {
  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  void identityCheckEndpointExists() throws Exception {
    Method method =
        ForgotPasswordController.class.getDeclaredMethod(
            "check",
            ForgotPasswordIdentityCheckRequest.class,
            String.class,
            String.class,
            String.class,
            String.class,
            String.class,
            String.class,
            org.springframework.http.server.reactive.ServerHttpResponse.class);

    PostMapping mapping = method.getAnnotation(PostMapping.class);
    assertThat(mapping).isNotNull();
    assertThat(mapping.value()).contains("/identity-check");
  }

  @Test
  void identityCheckRequiresCookieAndXsrfBeforeCallingUseCase() throws Exception {
    var useCase = new RecordingUseCase();
    var controller = controller(useCase);

    String json =
        objectMapper.writeValueAsString(
            controller
                .check(
                    new ForgotPasswordIdentityCheckRequest("ID", "A1234567"),
                    null,
                    null,
                    null,
                    null,
                    "req-1",
                    "en",
                    new MockServerHttpResponse())
                .block());

    assertThat(json).contains("err.forgot-password.system.unavailable");
    assertThat(useCase.called).isFalse();
  }

  @Test
  void identityCheckEchoesRequestIdAndDoesNotExposeAccountRefOrRawIdentityValue() {
    var useCase = new RecordingUseCase();
    var controller = controller(useCase);
    var response = new MockServerHttpResponse();

    JsonNode json =
        objectMapper.valueToTree(
            controller
                .check(
                    new ForgotPasswordIdentityCheckRequest("ID", "A1234567"),
                    "afs_abc",
                    "csrf_abc",
                    "https://portal.example",
                    "https://portal.example/page",
                    "req-1",
                    "en",
                    response)
                .block());

    assertThat(useCase.called).isTrue();
    assertThat(useCase.command.sessionId().value()).isEqualTo("afs_abc");
    assertThat(useCase.command.csrfToken()).isEqualTo("csrf_abc");
    assertThat(useCase.command.identityType()).isEqualTo("ID");
    assertThat(useCase.command.identityValue()).isEqualTo("A1234567");
    assertThat(response.getHeaders().getFirst("X-Request-Id")).isEqualTo("req-1");
    assertThat(json.at("/body/status").asText()).isEqualTo("SUCCESS");
    assertThat(json.at("/body/result/nextStep").asText()).isEqualTo("ENTER_DETAILS");
    assertThat(json.toString())
        .doesNotContain("A1234567", "Account-Ref", "auth_flow_sid", "csrf_abc");
  }

  @Test
  void identityRequestToStringRedactsIdentityValue() {
    assertThat(new ForgotPasswordIdentityCheckRequest("ID", "A1234567").toString())
        .doesNotContain("A1234567")
        .contains("identityType=ID");
  }

  private static final class RecordingUseCase implements VerifyForgotPasswordIdentityUseCase {
    private boolean called;
    private VerifyForgotPasswordIdentityCommand command;

    @Override
    public Mono<VerifyForgotPasswordIdentityResult> verify(
        VerifyForgotPasswordIdentityCommand command) {
      called = true;
      this.command = command;
      return Mono.just(
          VerifyForgotPasswordIdentityResult.of(VerifyForgotPasswordIdentityStatus.VERIFIED));
    }
  }

  private static ForgotPasswordController controller(
      VerifyForgotPasswordIdentityUseCase identityCheckUseCase) {
    return new ForgotPasswordController(
        command -> Mono.error(new AssertionError("start use case must not be called")),
        new ForgotPasswordStartWebMapper(Duration.ofMinutes(5)),
        identityCheckUseCase,
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
  }
}
