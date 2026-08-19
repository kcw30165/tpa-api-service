package com.bct.ngtpa.apiservice.adapter.in.web.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.adapter.in.web.auth.config.AuthWorkflowProperties;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.login.LoginController;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.login.mapper.LoginOtpVerifyWebMapper;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.registration.config.*;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.session.AuthenticatedSessionCookieWriter;
import com.bct.ngtpa.apiservice.adapter.in.web.session.config.AuthFlowSessionProperties;
import com.bct.ngtpa.apiservice.application.dto.auth.login.account.*;
import com.bct.ngtpa.apiservice.application.dto.session.authenticated.AuthenticatedSessionId;
import com.bct.ngtpa.apiservice.application.usecase.auth.login.otp.verify.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpResponse;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class AuthLoginOtpVerifyCookiePublicationTest {
  @Test
  void successfulVerificationPublishesOnlyPortalCookieAndReturnsCsrfInBody() {
    var account =
        new LoginSessionAccount(
            new LoginAccountRef("acc_abcdefghijklmnopqrstuvwx"),
            "DB",
            "00000000012",
            "0001",
            "TR",
            "OE",
            "ACTIVE",
            "MEMBER",
            null,
            null);
    var result =
        new VerifyLoginOtpResult(
            VerifyLoginOtpStatus.ACCOUNTS_READY,
            null,
            true,
            List.of(account),
            new AuthenticatedSessionId("sid_abcdefghijklmnopqrstuvwx"),
            "fresh_authenticated_csrf");
    var response = new MockServerHttpResponse();
    var controller =
        new LoginController(
            null,
            null,
            AuthFlowCookieWriter.defaultWriter(),
            null,
            null,
            new XsrfCookieWriter(new AuthFlowSessionProperties()),
            null,
            null,
            null,
            null,
            command -> Mono.just(result),
            new LoginOtpVerifyWebMapper(),
            new AuthenticatedSessionCookieWriter(new AuthWorkflowProperties.AuthenticatedCookie()));

    StepVerifier.create(
            controller.verify(
                new com.bct.ngtpa.apiservice.adapter.in.web.auth.login.dto.LoginOtpVerifyRequest(
                    "123456"),
                "afs_opaque",
                "pre_auth_csrf",
                "idem_12345678",
                "req",
                "en",
                null,
                null,
                response))
        .assertNext(
            entity -> {
              assertThat(entity.getBody().success()).isTrue();
              assertThat(entity.getBody().result().data().csrfToken())
                  .isEqualTo("fresh_authenticated_csrf");
            })
        .verifyComplete();

    assertThat(response.getCookies().getFirst("portal_sid")).isNotNull();
    assertThat(response.getCookies().get("XSRF-TOKEN"))
        .allSatisfy(cookie -> assertThat(cookie.getPath()).isEqualTo("/api/v1/auth"));
    assertThat(response.getCookies().get("XSRF-TOKEN"))
        .allSatisfy(cookie -> assertThat(cookie.getMaxAge().isZero()).isTrue());
  }
}
