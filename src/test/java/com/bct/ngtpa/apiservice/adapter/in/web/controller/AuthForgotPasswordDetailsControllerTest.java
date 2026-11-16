package com.bct.ngtpa.apiservice.adapter.in.web.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;

import com.bct.ngtpa.apiservice.adapter.in.web.auth.forgotpassword.ForgotPasswordController;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.forgotpassword.ForgotPasswordHttpResponses;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.forgotpassword.dto.ForgotPasswordDetailsRequest;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.forgotpassword.mapper.ForgotPasswordCancelWebMapper;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.forgotpassword.mapper.ForgotPasswordCompleteWebMapper;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.forgotpassword.mapper.ForgotPasswordDetailsOtpWebMapper;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.forgotpassword.mapper.ForgotPasswordIdentityWebMapper;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.forgotpassword.mapper.ForgotPasswordStartWebMapper;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.registration.config.AuthFlowCookieWriter;
import com.bct.ngtpa.apiservice.adapter.in.web.response.ApiStatus;
import com.bct.ngtpa.apiservice.application.port.in.ProcessForgotPasswordDetailsOtpUseCase;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.detailsotp.ForgotPasswordDetailsOtpCommand;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.detailsotp.ForgotPasswordDetailsOtpResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.reflect.Method;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpResponse;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
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
            org.springframework.http.server.reactive.ServerHttpResponse.class,
            org.springframework.web.server.ServerWebExchange.class);

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
                    new MockServerHttpResponse(),
                    exchange())
                .block());

    assertThat(json).contains("err.system.unavailable");
    assertThat(useCase.called).isFalse();
  }

  @Test
  void detailsEndpointEchoesRequestIdAndDoesNotExposeSensitiveFields() {
    var useCase = new RecordingUseCase();
    var controller = controller(useCase);
    var response = new MockServerHttpResponse();
    var exchange = exchange();

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
                    response,
                    exchange)
                .block());

    assertThat(useCase.called).isTrue();
    assertThat(useCase.command.sessionId().value()).isEqualTo("afs_abc");
    assertThat(useCase.command.csrfToken()).isEqualTo("csrf_abc");
    assertThat(useCase.command.dateOfBirth()).isEqualTo("31/01/1980");
    assertThat(useCase.command.email()).isEqualTo("member@example.com");
    assertThat(useCase.command.idempotencyKey()).isEqualTo("idem-1");
    assertThat(response.getHeaders().getFirst("X-Request-Id")).isEqualTo("req-1");
    assertThat(
            com.bct.ngtpa.apiservice.adapter.in.web.filter.TerminalIncidentHttpObservation
                .isOwned(exchange))
        .isFalse();
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
  void systemUnavailableResultUsesExistingPublicErrorAndDoesNotMutateCookies() {
    var useCase =
        (ProcessForgotPasswordDetailsOtpUseCase)
            command ->
                Mono.just(
                    new ForgotPasswordDetailsOtpResult(
                        ForgotPasswordDetailsOtpResult.Status.SYSTEM_UNAVAILABLE,
                        null,
                        0,
                        0,
                        List.of(), false));
    var response = new MockServerHttpResponse();
    var exchange = exchange();

    var publicResponse =
        controller(useCase)
            .details(
                new ForgotPasswordDetailsRequest("31/01/1980", "member@example.com", null),
                "afs_abc",
                "csrf_abc",
                "https://portal.example",
                "https://portal.example/page",
                "req-1",
                "en",
                "idem-1",
                response,
                exchange)
            .block();

    assertThat(publicResponse.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    assertThat(publicResponse.getBody().status()).isEqualTo(ApiStatus.SYSTEM_ERROR);
    assertThat(publicResponse.getBody().errors())
        .singleElement()
        .satisfies(
            error -> {
              assertThat(error.code()).isEqualTo("err.system.unavailable");
              assertThat(error.type()).isEqualTo("SYSTEM");
              assertThat(error.targets()).isEmpty();
              assertThat(error.message()).isNull();
            });
    assertThat(response.getCookies()).isEmpty();
    assertThat(response.getHeaders().getFirst("X-Request-Id")).isEqualTo("req-1");
    assertThat(
            com.bct.ngtpa.apiservice.adapter.in.web.filter.TerminalIncidentHttpObservation
                .isOwned(exchange))
        .isFalse();
  }

  @Test
  void publishedIncidentMarksOnlyTheCurrentDetailsResponse() throws Exception {
    var useCase =
        (ProcessForgotPasswordDetailsOtpUseCase)
            command ->
                Mono.just(
                    new ForgotPasswordDetailsOtpResult(
                        ForgotPasswordDetailsOtpResult.Status.SYSTEM_UNAVAILABLE,
                        null,
                        0,
                        0,
                        List.of(),
                        true));
    var response = new MockServerHttpResponse();
    var exchange = exchange();
    var publicResponse =
        controller(useCase)
            .details(
                new ForgotPasswordDetailsRequest("31/01/1980", "member@example.com", null),
                "afs_abc",
                "csrf_abc",
                "https://portal.example",
                "https://portal.example/page",
                "req-1",
                "en",
                "idem-1",
                response,
                exchange)
            .block();

    assertThat(publicResponse.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    assertThat(
            com.bct.ngtpa.apiservice.adapter.in.web.filter.TerminalIncidentHttpObservation
                .isOwned(exchange))
        .isTrue();
    assertThat(objectMapper.writeValueAsString(publicResponse.getBody()))
        .doesNotContain("incidentPublished");
  }

  @Test
  void responseConstructionFailureDoesNotMarkTheDetailsResponseAsOwned() {
    var useCase =
        (ProcessForgotPasswordDetailsOtpUseCase)
            command ->
                Mono.just(
                    new ForgotPasswordDetailsOtpResult(
                        ForgotPasswordDetailsOtpResult.Status.SYSTEM_UNAVAILABLE,
                        null,
                        0,
                        0,
                        List.of(),
                        true));
    var exchange = exchange();

    try (var responseFactory = mockStatic(ForgotPasswordHttpResponses.class)) {
      responseFactory
          .when(() -> ForgotPasswordHttpResponses.toResponse(any()))
          .thenThrow(new IllegalStateException());
      var detailsResponse =
          controller(useCase)
              .details(
                  new ForgotPasswordDetailsRequest("31/01/1980", "member@example.com", null),
                  "afs_abc",
                  "csrf_abc",
                  "https://portal.example",
                  "https://portal.example/page",
                  "req-1",
                  "en",
                  "idem-1",
                  new MockServerHttpResponse(),
                  exchange);
      assertThatThrownBy(detailsResponse::block).isInstanceOf(IllegalStateException.class);
    }
    assertThat(
            com.bct.ngtpa.apiservice.adapter.in.web.filter.TerminalIncidentHttpObservation
                .isOwned(exchange))
        .isFalse();
  }

  @Test
  void memberMismatchKeepsExistingBusinessRejectedContract() {
    var useCase =
        (ProcessForgotPasswordDetailsOtpUseCase)
            command ->
                Mono.just(
                    new ForgotPasswordDetailsOtpResult(
                        ForgotPasswordDetailsOtpResult.Status.BUSINESS_REJECTED,
                        null,
                        0,
                        0,
                        List.of("PAGE"), false));
    var response = new MockServerHttpResponse();
    var exchange = exchange();

    var publicResponse =
        controller(useCase)
            .details(
                new ForgotPasswordDetailsRequest("31/01/1980", "member@example.com", null),
                "afs_abc",
                "csrf_abc",
                "https://portal.example",
                "https://portal.example/page",
                "req-1",
                "en",
                "idem-1",
                response,
                exchange)
            .block();

    assertThat(publicResponse.getStatusCode().value())
        .isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT.value());
    assertThat(publicResponse.getBody().status()).isEqualTo(ApiStatus.BUSINESS_REJECTED);
    assertThat(publicResponse.getBody().errors())
        .singleElement()
        .satisfies(
            error -> {
              assertThat(error.code()).isEqualTo("err.forgot-password.member.notFound");
              assertThat(error.type()).isEqualTo("BUSINESS");
              assertThat(error.targets()).isEmpty();
              assertThat(error.message()).isNull();
            });
    assertThat(response.getCookies()).isEmpty();
    assertThat(
            com.bct.ngtpa.apiservice.adapter.in.web.filter.TerminalIncidentHttpObservation
                .isOwned(exchange))
        .isFalse();
  }

  @Test
  void missingXsrfTokenShortCircuitsDetailsUseCase() {
    var useCase = new RecordingUseCase();
    var response = new MockServerHttpResponse();
    var exchange = exchange();

    var publicResponse =
        controller(useCase)
            .details(
                new ForgotPasswordDetailsRequest("31/01/1980", "member@example.com", null),
                "afs_abc",
                null,
                "https://portal.example",
                "https://portal.example/page",
                "req-1",
                "en",
                "idem-1",
                response,
                exchange)
            .block();

    assertThat(useCase.called).isFalse();
    assertThat(publicResponse.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    assertThat(response.getCookies()).isEmpty();
    assertThat(
            com.bct.ngtpa.apiservice.adapter.in.web.filter.TerminalIncidentHttpObservation
                .isOwned(exchange))
        .isFalse();
  }

  private static MockServerWebExchange exchange() {
    return MockServerWebExchange.from(
        MockServerHttpRequest.post("/api/v1/auth/forgot-password/details").build());
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
              java.util.List.of(), false));
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
