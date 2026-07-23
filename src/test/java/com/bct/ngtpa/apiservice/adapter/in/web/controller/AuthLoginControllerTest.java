package com.bct.ngtpa.apiservice.adapter.in.web.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.adapter.in.web.auth.login.dto.LoginStartRequest;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.login.mapper.LoginStartWebMapper;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.registration.config.AuthFlowCookieProperties;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.registration.config.AuthFlowCookieWriter;
import com.bct.ngtpa.apiservice.application.port.in.StartLoginUseCase;
import com.bct.ngtpa.apiservice.application.usecase.auth.login.start.*;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpResponse;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class AuthLoginControllerTest {
        @Test
        void successfulStartWritesCookieAndReturnsNoSessionIdInBody() {
                AtomicReference<StartLoginCommand> command = new AtomicReference<>();
                StartLoginUseCase useCase = value -> {
                        command.set(value);
                        return Mono.just(new StartLoginResult(
                                        StartLoginStatus.STARTED, "afs_secret", "csrf_secret", 900,
                                        List.of(new LoginDestinationChoice("dst_opaque", "email",
                                                        "m*****@example.com")),
                                        0, null));
                };
                var controller = new AuthLoginController(
                                useCase,
                                new LoginStartWebMapper(),
                                new AuthFlowCookieWriter(new AuthFlowCookieProperties(
                                                "auth_flow_sid", "/api/v1/auth", Duration.ofSeconds(900),
                                                true, true, "None")));
                var response = new MockServerHttpResponse();

                StepVerifier.create(controller.startLogin(
                                new LoginStartRequest("Member", "Exact Password"),
                                "login_key_opaque", "req-1", "en", response))
                                .assertNext(entity -> {
                                        assertThat(entity.getStatusCode()).isEqualTo(HttpStatus.OK);
                                        assertThat(entity.getBody().result().data().csrfToken())
                                                        .isEqualTo("csrf_secret");
                                        assertThat(entity.toString()).doesNotContain("afs_secret", "Exact Password");
                                })
                                .verifyComplete();

                assertThat(response.getCookies().getFirst("auth_flow_sid").getValue())
                                .isEqualTo("afs_secret");
                assertThat(command.get().idempotencyKey()).isEqualTo("login_key_opaque");
        }

        @Test
        void missingIdempotencyKeyDoesNotInvokeUseCase() {
                StartLoginUseCase useCase = value -> Mono.error(new AssertionError("must not execute"));
                var controller = new AuthLoginController(
                                useCase, new LoginStartWebMapper(), AuthFlowCookieWriter.defaultWriter());

                StepVerifier.create(controller.startLogin(
                                new LoginStartRequest("member", "Exact"),
                                null, "req-1", "en", new MockServerHttpResponse()))
                                .assertNext(entity -> {
                                        assertThat(entity.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                                        assertThat(entity.getBody().errors()).singleElement()
                                                        .extracting(error -> error.code())
                                                        .isEqualTo("err.workflow.idempotency.missing");
                                })
                                .verifyComplete();
        }
}
