package com.bct.ngtpa.apiservice.integration.login;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.adapter.in.web.auth.login.LoginController;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.login.dto.LoginStartRequest;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.login.mapper.LoginStartWebMapper;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.registration.config.AuthFlowCookieProperties;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.registration.config.AuthFlowCookieWriter;
import com.bct.ngtpa.apiservice.application.port.in.StartLoginUseCase;
import com.bct.ngtpa.apiservice.application.usecase.auth.login.start.*;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpResponse;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class LoginStartEvidenceIntegrationTest {
  private final AuthFlowCookieWriter cookieWriter =
      new AuthFlowCookieWriter(
          new AuthFlowCookieProperties(
              "auth_flow_sid", "/api/v1/auth", Duration.ofSeconds(900), true, true, "None"));

  @Test
  void freshAndCompletedReplayBothRestoreCookieAndPublicContract() {
    for (StartLoginStatus status :
        List.of(StartLoginStatus.STARTED, StartLoginStatus.COMPLETED_REPLAY)) {
      StartLoginUseCase useCase =
          command ->
              Mono.just(
                  new StartLoginResult(
                      status,
                      "afs_private",
                      "csrf_private",
                      900,
                      List.of(new LoginDestinationChoice("dst_opaque", "sms", "****4567")),
                      0,
                      "rpl_private"));
      var response = new MockServerHttpResponse();
      var controller =
          new LoginController(
              useCase,
              new LoginStartWebMapper(),
              cookieWriter,
              null,
              null,
              null,
              null,
              null,
              null,
              null,
              null,
              null);

      StepVerifier.create(
              controller.startLogin(
                  new LoginStartRequest("member", "Exact Password"),
                  "login_key_opaque",
                  "req-evidence",
                  "en",
                  response))
          .assertNext(
              entity -> {
                assertThat(entity.getStatusCode()).isEqualTo(HttpStatus.OK);
                assertThat(entity.getBody().result().data().csrfToken()).isEqualTo("csrf_private");
                assertThat(entity.toString())
                    .doesNotContain("afs_private", "rpl_private", "Exact Password");
              })
          .verifyComplete();

      var cookie = response.getCookies().getFirst("auth_flow_sid");
      assertThat(cookie.getValue()).isEqualTo("afs_private");
      assertThat(cookie.isHttpOnly()).isTrue();
      assertThat(cookie.isSecure()).isTrue();
      assertThat(cookie.getSameSite()).isEqualTo("None");
      assertThat(cookie.getPath()).isEqualTo("/api/v1/auth");
      assertThat(response.getHeaders().getFirst("X-Request-Id")).isEqualTo("req-evidence");
    }
  }

  @Test
  void missingRequestIdAndReactiveFailureAreContainedWithoutSuccessCookie() {
    AtomicInteger calls = new AtomicInteger();
    StartLoginUseCase useCase =
        command -> {
          calls.incrementAndGet();
          return Mono.error(new IllegalStateException("internal secret detail"));
        };
    var controller =
        new LoginController(
            useCase,
            new LoginStartWebMapper(),
            cookieWriter,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null);

    StepVerifier.create(
            controller.startLogin(
                new LoginStartRequest("member", "Exact"),
                "login_key_opaque",
                null,
                "en",
                new MockServerHttpResponse()))
        .assertNext(
            entity -> {
              assertThat(entity.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
              assertThat(entity.getBody().errors())
                  .singleElement()
                  .extracting(error -> error.code())
                  .isEqualTo("err.request.id.missing");
            })
        .verifyComplete();
    assertThat(calls).hasValue(0);

    var failureResponse = new MockServerHttpResponse();
    StepVerifier.create(
            controller.startLogin(
                new LoginStartRequest("member", "Exact"),
                "login_key_opaque",
                "req-failure",
                "en",
                failureResponse))
        .assertNext(
            entity -> {
              assertThat(entity.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
              assertThat(entity.toString()).doesNotContain("internal secret detail");
            })
        .verifyComplete();
    assertThat(failureResponse.getCookies()).doesNotContainKey("auth_flow_sid");
    assertThat(failureResponse.getHeaders().getFirst("X-Request-Id")).isEqualTo("req-failure");
  }
}
