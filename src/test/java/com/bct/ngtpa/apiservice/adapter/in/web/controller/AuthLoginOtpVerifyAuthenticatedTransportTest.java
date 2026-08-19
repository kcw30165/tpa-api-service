package com.bct.ngtpa.apiservice.adapter.in.web.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.adapter.in.web.auth.login.mapper.LoginOtpVerifyWebMapper;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.registration.config.AuthFlowCookieWriter;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.registration.config.XsrfCookieWriter;
import com.bct.ngtpa.apiservice.adapter.in.web.session.config.AuthFlowSessionProperties;
import com.bct.ngtpa.apiservice.application.dto.auth.login.account.LoginAccountRef;
import com.bct.ngtpa.apiservice.application.dto.auth.login.account.LoginSessionAccount;
import com.bct.ngtpa.apiservice.application.dto.session.authenticated.AuthenticatedSessionId;
import com.bct.ngtpa.apiservice.application.port.in.VerifyLoginOtpUseCase;
import com.bct.ngtpa.apiservice.application.usecase.auth.login.otp.verify.VerifyLoginOtpResult;
import com.bct.ngtpa.apiservice.application.usecase.auth.login.otp.verify.VerifyLoginOtpStatus;
import com.bct.ngtpa.apiservice.shared.web.RequestHeaderContextKeys;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpResponse;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class AuthLoginOtpVerifyAuthenticatedTransportTest {
  private static final String SID = "sid_abcdefghijklmnopqrstuvwx";

  @Test
  void successfulVerificationPublishesAuthenticatedTransportAndClearsPreAuthCookies() {
    VerifyLoginOtpUseCase useCase =
        command ->
            Mono.just(
                new VerifyLoginOtpResult(
                    VerifyLoginOtpStatus.ACCOUNTS_READY,
                    null,
                    true,
                    List.of(account()),
                    new AuthenticatedSessionId(SID),
                    "authenticated_csrf_token"));
    AuthFlowSessionProperties properties = new AuthFlowSessionProperties();
    AuthLoginOtpVerifyController controller =
        new AuthLoginOtpVerifyController(
            useCase,
            new LoginOtpVerifyWebMapper(),
            AuthFlowCookieWriter.from(properties),
            new XsrfCookieWriter(properties),
            new com.bct.ngtpa.apiservice.adapter.in.web.auth.session
                .AuthenticatedSessionCookieWriter(
                new com.bct.ngtpa.apiservice.adapter.in.web.auth.config.AuthWorkflowProperties
                    .AuthenticatedCookie()));
    MockServerHttpResponse response = new MockServerHttpResponse();

    StepVerifier.create(
            controller.verify(
                new com.bct.ngtpa.apiservice.adapter.in.web.auth.login.dto.LoginOtpVerifyRequest(
                    "123456"),
                "afs_opaque",
                "csrf",
                "idem",
                "req",
                "en",
                "https://portal",
                null,
                response))
        .assertNext(entity -> assertThat(entity.getStatusCode()).isEqualTo(HttpStatus.OK))
        .verifyComplete();

    assertThat(
            response.getHeaders().getFirst(RequestHeaderContextKeys.AUTHENTICATED_SESSION_HEADER))
        .isNull();
    assertThat(
            response.getCookies().getFirst(properties.getCookie().getName()).getMaxAge().isZero())
        .isTrue();
    assertThat(response.getCookies().get(properties.getCsrf().getCookieName())).hasSize(1);
    var clearedPreAuthXsrf = response.getCookies().getFirst(properties.getCsrf().getCookieName());
    assertThat(clearedPreAuthXsrf).isNotNull();
    assertThat(clearedPreAuthXsrf.getPath()).isEqualTo(properties.getCsrf().getCookiePath());
    assertThat(clearedPreAuthXsrf.getMaxAge().isZero()).isTrue();
  }

  @Test
  void failedVerificationNeverPublishesSid() {
    VerifyLoginOtpUseCase useCase =
        command -> Mono.just(new VerifyLoginOtpResult(VerifyLoginOtpStatus.INVALID, 3, false));
    AuthFlowSessionProperties properties = new AuthFlowSessionProperties();
    AuthLoginOtpVerifyController controller =
        new AuthLoginOtpVerifyController(
            useCase,
            new LoginOtpVerifyWebMapper(),
            AuthFlowCookieWriter.from(properties),
            new XsrfCookieWriter(properties),
            new com.bct.ngtpa.apiservice.adapter.in.web.auth.session
                .AuthenticatedSessionCookieWriter(
                new com.bct.ngtpa.apiservice.adapter.in.web.auth.config.AuthWorkflowProperties
                    .AuthenticatedCookie()));
    MockServerHttpResponse response = new MockServerHttpResponse();

    StepVerifier.create(
            controller.verify(
                new com.bct.ngtpa.apiservice.adapter.in.web.auth.login.dto.LoginOtpVerifyRequest(
                    "000000"),
                "afs_opaque",
                "csrf",
                "idem",
                "req",
                "en",
                "https://portal",
                null,
                response))
        .assertNext(
            entity -> assertThat(entity.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY))
        .verifyComplete();

    assertThat(
            response.getHeaders().getFirst(RequestHeaderContextKeys.AUTHENTICATED_SESSION_HEADER))
        .isNull();
  }

  private LoginSessionAccount account() {
    return new LoginSessionAccount(
        new LoginAccountRef("acc_abcdefghijklmnopqrstuvwx"),
        "DB",
        "8802",
        "00000008802",
        "1",
        "0001",
        "DBA",
        "OE",
        null,
        null);
  }
}
