package com.bct.ngtpa.apiservice.adapter.in.web.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.adapter.in.web.auth.config.AuthWorkflowProperties;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.login.mapper.LoginOtpVerifyWebMapper;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.registration.config.AuthFlowCookieWriter;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.registration.config.XsrfCookieWriter;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.session.AuthenticatedCsrfCookieWriter;
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
        void successfulVerificationPublishesPortalAndAuthenticatedCsrfCookies() {
                var account = new LoginSessionAccount(
                                new LoginAccountRef("acc_abcdefghijklmnopqrstuvwx"),
                                "DB", "00000000012", "0001", "TR", "OE", "ACTIVE", "MEMBER", null, null);
                var result = new VerifyLoginOtpResult(
                                VerifyLoginOtpStatus.ACCOUNTS_READY,
                                null,
                                true,
                                List.of(account),
                                new AuthenticatedSessionId("sid_abcdefghijklmnopqrstuvwx"),
                                "fresh_authenticated_csrf");
                var properties = new AuthWorkflowProperties();
                properties.setLogin(new AuthWorkflowProperties.Login());
                var response = new MockServerHttpResponse();
                var controller = new AuthLoginOtpVerifyController(
                                command -> Mono.just(result),
                                new LoginOtpVerifyWebMapper(),
                                AuthFlowCookieWriter.defaultWriter(),
                                new XsrfCookieWriter(new AuthFlowSessionProperties()),
                                new AuthenticatedSessionCookieWriter(properties.getLogin().getSession().getCookie()),
                                new AuthenticatedCsrfCookieWriter(properties.getLogin().getSession().getCsrf()));

                StepVerifier.create(controller.verify(
                                new com.bct.ngtpa.apiservice.adapter.in.web.auth.login.dto.LoginOtpVerifyRequest(
                                                "123456"),
                                "afs_opaque", "pre_auth_csrf", "idem_12345678", "req", "en", null, null, response))
                                .assertNext(entity -> assertThat(entity.getBody().success()).isTrue())
                                .verifyComplete();

                var portal = response.getCookies().getFirst("portal_sid");
                var xsrf = response.getCookies().getFirst("XSRF-TOKEN");
                assertThat(portal).isNotNull();
                assertThat(portal.getValue()).isEqualTo("sid_abcdefghijklmnopqrstuvwx");
                assertThat(portal.isHttpOnly()).isTrue();
                assertThat(xsrf).isNotNull();
                assertThat(xsrf.getValue()).isEqualTo("fresh_authenticated_csrf");
                assertThat(xsrf.isHttpOnly()).isFalse();
        }
}
