package com.bct.ngtpa.apiservice.adapter.in.web.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.adapter.in.web.auth.forgotpassword.ForgotPasswordController;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.forgotpassword.dto.ForgotPasswordDetailsRequest;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.forgotpassword.mapper.ForgotPasswordCancelWebMapper;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.forgotpassword.mapper.ForgotPasswordCompleteWebMapper;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.forgotpassword.mapper.ForgotPasswordDetailsOtpWebMapper;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.forgotpassword.mapper.ForgotPasswordIdentityWebMapper;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.forgotpassword.mapper.ForgotPasswordStartWebMapper;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.registration.config.AuthFlowCookieWriter;
import com.bct.ngtpa.apiservice.application.port.in.ProcessForgotPasswordDetailsOtpUseCase;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.detailsotp.ForgotPasswordDetailsOtpCommand;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.detailsotp.ForgotPasswordDetailsOtpResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.reflect.Method;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpResponse;
import org.springframework.web.bind.annotation.PostMapping;
import reactor.core.publisher.Mono;

class AuthForgotPasswordDetailsControllerTest {
  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  void detailsEndpointExists() throws Exception {
    Method method =
        ForgotPasswordController.class.getDeclaredMethod(
            "details",
            ForgotPasswordDetailsRequest.class,
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
    assertThat(mapping.value()).contains("/details-check", "/details");
  }

  @Test
  void detailsEndpointRequiresCookieXsrfAndIdempotencyKey() throws Exception {
    var useCase = new RecordingUseCase();
    var controller = controller(useCase);

    String json =
        objectMapper.writeValueAsString(
            controller
                .details(
                    new ForgotPasswordDetailsRequest("31/01/1980", "member@example.com", null),
                    null,
                    "csrf",
                    null,
                    null,
                    "req-1",
                    "en",
                    "idem-1",
                    new MockServerHttpResponse())
                .block());

    assertThat(json).contains("err.system.unavailable");
    assertThat(useCase.called).isFalse();
  }

  @Test
  void detailsEndpointEchoesRequestIdAndDoesNotExposeSensitiveFields() {
    var useCase = new RecordingUseCase();
    var controller = controller(useCase);
    var response = new MockServerHttpResponse();

    JsonNode json =
        objectMapper.valueToTree(
            controller
                .details(
                    new ForgotPasswordDetailsRequest("31/01/1980", "member@example.com", null),
                    "afs_abc",
                    "csrf_abc",
                    "https://portal.example",
                    "https://portal.example/page",
                    "req-1",
                    "en",
                    "idem-1",
                    response)
                .block());

    assertThat(useCase.called).isTrue();
    assertThat(useCase.command.sessionId().value()).isEqualTo("afs_abc");
    assertThat(useCase.command.csrfToken()).isEqualTo("csrf_abc");
    assertThat(useCase.command.dateOfBirth()).isEqualTo("31/01/1980");
    assertThat(useCase.command.email()).isEqualTo("member@example.com");
    assertThat(useCase.command.idempotencyKey()).isEqualTo("idem-1");
    assertThat(response.getHeaders().getFirst("X-Request-Id")).isEqualTo("req-1");
    assertThat(json.at("/body/result/nextStep").asText()).isEqualTo("VERIFY_OTP");
    assertThat(json.toString())
        .doesNotContain(
            "31/01/1980",
            "member@example.com",
            "afs_abc",
            "csrf_abc",
            "Account-Ref",
            "auth_flow_sid");
  }

  @Test
  void detailsRequestToStringRedactsDobAndContactValues() {
    assertThat(
            new ForgotPasswordDetailsRequest("31/01/1980", "member@example.com", "91234567")
                .toString())
        .doesNotContain("31/01/1980", "member@example.com", "91234567")
        .contains("dateOfBirth=<redacted>", "email=<redacted>", "mobile=<redacted>");
  }

  private static final class RecordingUseCase implements ProcessForgotPasswordDetailsOtpUseCase {
    private boolean called;
    private ForgotPasswordDetailsOtpCommand command;

    @Override
    public Mono<ForgotPasswordDetailsOtpResult> process(ForgotPasswordDetailsOtpCommand command) {
      called = true;
      this.command = command;
      return Mono.just(
          new ForgotPasswordDetailsOtpResult(
              ForgotPasswordDetailsOtpResult.Status.SUCCESS,
              "m****r@example.com",
              300,
              60,
              java.util.List.of()));
    }
  }

  private static ForgotPasswordController controller(
      ProcessForgotPasswordDetailsOtpUseCase detailsCheckUseCase) {
    return new ForgotPasswordController(
        command -> Mono.error(new AssertionError("start use case must not be called")),
        new ForgotPasswordStartWebMapper(Duration.ofMinutes(5)),
        command -> Mono.error(new AssertionError("identity use case must not be called")),
        new ForgotPasswordIdentityWebMapper(),
        detailsCheckUseCase,
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
