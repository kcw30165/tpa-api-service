package com.bct.ngtpa.apiservice.integration.login;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.adapter.in.web.auth.config.AuthWorkflowProperties;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.login.LoginController;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.login.cancel.CancelLoginWebMapper;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.login.mapper.LoginOtpResendWebMapper;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.login.mapper.LoginOtpSendWebMapper;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.login.mapper.LoginOtpVerifyWebMapper;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.login.mapper.LoginStartWebMapper;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.registration.config.AuthFlowCookieProperties;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.registration.config.AuthFlowCookieWriter;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.session.AuthenticatedSessionCookieWriter;
import com.bct.ngtpa.apiservice.application.port.in.CancelLoginUseCase;
import com.bct.ngtpa.apiservice.application.port.in.ResendLoginOtpUseCase;
import com.bct.ngtpa.apiservice.application.port.in.SendLoginOtpUseCase;
import com.bct.ngtpa.apiservice.application.port.in.StartLoginUseCase;
import com.bct.ngtpa.apiservice.application.port.in.VerifyLoginOtpUseCase;
import com.bct.ngtpa.apiservice.application.usecase.auth.login.cancel.CancelLoginCommand;
import com.bct.ngtpa.apiservice.application.usecase.auth.login.cancel.CancelLoginResult;
import com.bct.ngtpa.apiservice.application.usecase.auth.login.cancel.CancelLoginStatus;
import com.bct.ngtpa.apiservice.application.usecase.auth.login.start.LoginDestinationChoice;
import com.bct.ngtpa.apiservice.application.usecase.auth.login.start.StartLoginResult;
import com.bct.ngtpa.apiservice.application.usecase.auth.login.start.StartLoginStatus;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpCookie;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

class LoginStartCancellationWebIntegrationTest {
  private static final String AUTH_FLOW_SESSION_ID = "afs_integration_opaque";
  private static final String CSRF_TOKEN = "csrf_integration_secret";

  private final AtomicInteger startCalls = new AtomicInteger();
  private final AtomicInteger cancelCalls = new AtomicInteger();
  private final AtomicInteger otpCalls = new AtomicInteger();
  private final AtomicReference<CancelLoginCommand> cancelCommand = new AtomicReference<>();
  private WebTestClient client;

  @BeforeEach
  void setUp() {
    startCalls.set(0);
    cancelCalls.set(0);
    otpCalls.set(0);
    cancelCommand.set(null);

    StartLoginUseCase start =
        command -> {
          startCalls.incrementAndGet();
          return Mono.just(
              new StartLoginResult(
                  StartLoginStatus.STARTED,
                  AUTH_FLOW_SESSION_ID,
                  CSRF_TOKEN,
                  900,
                  List.of(
                      new LoginDestinationChoice(
                          "dst_integration_opaque", "email", "m*****@example.com")),
                  0,
                  null));
        };

    CancelLoginUseCase cancel =
        command -> {
          cancelCalls.incrementAndGet();
          cancelCommand.set(command);
          return Mono.just(
              new CancelLoginResult(CancelLoginStatus.CANCELLED, true, true, List.of()));
        };

    SendLoginOtpUseCase sendOtp =
        command -> {
          otpCalls.incrementAndGet();
          return Mono.error(new AssertionError("OTP send must not execute"));
        };
    ResendLoginOtpUseCase resendOtp =
        command -> {
          otpCalls.incrementAndGet();
          return Mono.error(new AssertionError("OTP resend must not execute"));
        };
    VerifyLoginOtpUseCase verifyOtp =
        command -> {
          otpCalls.incrementAndGet();
          return Mono.error(new AssertionError("OTP verification must not execute"));
        };

    var cookieWriter =
        new AuthFlowCookieWriter(
            new AuthFlowCookieProperties(
                "auth_flow_sid", "/api/v1/auth", Duration.ofSeconds(900), true, true, "None"));
    var authenticatedCookie = new AuthWorkflowProperties.AuthenticatedCookie();
    var controller =
        new LoginController(
            start,
            new LoginStartWebMapper(),
            cookieWriter,
            cancel,
            new CancelLoginWebMapper(),
            sendOtp,
            new LoginOtpSendWebMapper(),
            resendOtp,
            new LoginOtpResendWebMapper(),
            verifyOtp,
            new LoginOtpVerifyWebMapper(),
            new AuthenticatedSessionCookieWriter(authenticatedCookie));

    client = WebTestClient.bindToController(controller).build();
  }

  @Test
  void loginStartThenCancellationUsesRealWebMappingAndClearsPreAuthCookie() {
    var startResult =
        client
            .post()
            .uri("/api/v1/auth/login")
            .header("X-Request-Id", "login-integration-start")
            .header("Idempotency-Key", "login-integration-idem")
            .header("Accept-Language", "en")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("{\"username\":\"member\",\"password\":\"Exact Password\"}")
            .exchange()
            .expectStatus()
            .isOk()
            .expectHeader()
            .valueEquals("X-Request-Id", "login-integration-start")
            .expectBody()
            .jsonPath("$.success")
            .isEqualTo(true)
            .jsonPath("$.status")
            .isEqualTo("SUCCESS")
            .jsonPath("$.result.nextStep")
            .isEqualTo("SELECT_OTP_DESTINATION")
            .jsonPath("$.result.data.csrfToken")
            .isEqualTo(CSRF_TOKEN)
            .jsonPath("$.result.data.destinations.length()")
            .isEqualTo(1)
            .returnResult();

    HttpCookie authFlowCookie = startResult.getResponseCookies().getFirst("auth_flow_sid");
    assertThat(authFlowCookie).isNotNull();
    assertThat(authFlowCookie.getValue()).isEqualTo(AUTH_FLOW_SESSION_ID);

    client
        .post()
        .uri("/api/v1/auth/login/cancel")
        .cookie("auth_flow_sid", authFlowCookie.getValue())
        .header("X-XSRF-TOKEN", CSRF_TOKEN)
        .header("X-Request-Id", "login-integration-cancel")
        .exchange()
        .expectStatus()
        .isOk()
        .expectHeader()
        .valueEquals("X-Request-Id", "login-integration-cancel")
        .expectCookie()
        .valueEquals("auth_flow_sid", "")
        .expectCookie()
        .maxAge("auth_flow_sid", Duration.ZERO)
        .expectBody()
        .jsonPath("$.success")
        .isEqualTo(true)
        .jsonPath("$.status")
        .isEqualTo("SUCCESS")
        .jsonPath("$.result.nextStep")
        .isEqualTo("RETURN_TO_LOGIN")
        .jsonPath("$.result.data.actions.length()")
        .isEqualTo(0)
        .jsonPath("$.result.data.csrfToken")
        .doesNotExist();

    assertThat(startCalls).hasValue(1);
    assertThat(cancelCalls).hasValue(1);
    assertThat(otpCalls).hasValue(0);
    assertThat(cancelCommand.get()).isNotNull();
    assertThat(cancelCommand.get().authFlowSessionId().value()).isEqualTo(AUTH_FLOW_SESSION_ID);
    assertThat(cancelCommand.get().csrfToken()).isEqualTo(CSRF_TOKEN);
    assertThat(cancelCommand.get().correlationId()).isEqualTo("login-integration-cancel");
  }

  @Test
  void missingSessionCancellationIsIdempotentAndDoesNotInvokeUseCases() {
    client
        .post()
        .uri("/api/v1/auth/login/cancel")
        .header("X-Request-Id", "login-integration-missing-session")
        .exchange()
        .expectStatus()
        .isOk()
        .expectCookie()
        .valueEquals("auth_flow_sid", "")
        .expectCookie()
        .maxAge("auth_flow_sid", Duration.ZERO)
        .expectBody()
        .jsonPath("$.success")
        .isEqualTo(true)
        .jsonPath("$.result.nextStep")
        .isEqualTo("RETURN_TO_LOGIN");

    assertThat(startCalls).hasValue(0);
    assertThat(cancelCalls).hasValue(0);
    assertThat(otpCalls).hasValue(0);
  }
}
