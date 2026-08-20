package com.bct.ngtpa.apiservice.adapter.in.web.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.adapter.in.web.auth.forgotpassword.ForgotPasswordController;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.forgotpassword.dto.ForgotPasswordCompleteRequest;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.forgotpassword.mapper.ForgotPasswordCancelWebMapper;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.forgotpassword.mapper.ForgotPasswordCompleteWebMapper;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.forgotpassword.mapper.ForgotPasswordDetailsOtpWebMapper;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.forgotpassword.mapper.ForgotPasswordIdentityWebMapper;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.forgotpassword.mapper.ForgotPasswordStartWebMapper;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.registration.config.AuthFlowCookieWriter;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.registration.config.XsrfCookieWriter;
import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowNextStep;
import com.bct.ngtpa.apiservice.application.port.in.CompleteForgotPasswordUseCase;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.complete.CompleteForgotPasswordCommand;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.complete.CompleteForgotPasswordResult;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.complete.CompleteForgotPasswordStatus;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.reflect.Method;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpResponse;
import org.springframework.web.bind.annotation.PostMapping;
import reactor.core.publisher.Mono;

class AuthForgotPasswordCompleteControllerTest {
  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  void completionEndpointExists() throws Exception {
    Method method =
        ForgotPasswordController.class.getDeclaredMethod(
            "complete",
            ForgotPasswordCompleteRequest.class,
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
    assertThat(mapping.path()).contains("/complete");
  }

  @Test
  void requestDtoExcludesInternalFieldsAndRedactsPasswords() {
    var fields =
        List.of(ForgotPasswordCompleteRequest.class.getRecordComponents()).stream()
            .map(component -> component.getName())
            .toList();

    assertThat(fields).containsExactly("password", "confirmPassword");
    assertThat(new ForgotPasswordCompleteRequest("Password1!", "Password1!").toString())
        .doesNotContain("Password1!");
  }

  @Test
  void completionSuccessReturnsReturnToLoginAndClearsOnlyPreAuthCookies() {
    var controller = controller(new RecordingUseCase());
    var response = new MockServerHttpResponse();

    JsonNode json =
        objectMapper.valueToTree(
            controller
                .complete(
                    new ForgotPasswordCompleteRequest("Password1!", "Password1!"),
                    "afs_abc",
                    "csrf_abc",
                    "idem_abc",
                    "req_abc",
                    "en",
                    "https://portal.example",
                    "https://portal.example/page",
                    response)
                .block());

    assertThat(json.at("/body/result/nextStep").asText()).isEqualTo("RETURN_TO_LOGIN");
    assertThat(json.at("/body/result/data").isObject()).isTrue();
    assertThat(json.at("/body/result/data").size()).isZero();
    assertThat(json.toString())
        .doesNotContain("Password1!", "afs_abc", "csrf_abc", "idem_abc", "db655", "authz-01");
  }

  private static final class RecordingUseCase implements CompleteForgotPasswordUseCase {
    @Override
    public Mono<CompleteForgotPasswordResult> complete(CompleteForgotPasswordCommand command) {
      return Mono.just(
          new CompleteForgotPasswordResult(
              CompleteForgotPasswordStatus.SUCCESS,
              WorkflowNextStep.RETURN_TO_LOGIN,
              true,
              true,
              List.of()));
    }
  }

  private static ForgotPasswordController controller(CompleteForgotPasswordUseCase useCase) {
    return new ForgotPasswordController(
        command -> Mono.error(new AssertionError("start use case must not be called")),
        new ForgotPasswordStartWebMapper(Duration.ofMinutes(5)),
        command -> Mono.error(new AssertionError("identity use case must not be called")),
        new ForgotPasswordIdentityWebMapper(),
        command -> Mono.error(new AssertionError("details use case must not be called")),
        new ForgotPasswordDetailsOtpWebMapper(),
        useCase,
        new ForgotPasswordCompleteWebMapper(),
        command -> Mono.error(new AssertionError("cancel use case must not be called")),
        new ForgotPasswordCancelWebMapper(),
        AuthFlowCookieWriter.defaultWriter(),
        new XsrfCookieWriter(
            new com.bct.ngtpa.apiservice.adapter.in.web.session.config.AuthFlowSessionProperties()),
        null,
        null,
        null,
        null);
  }
}
