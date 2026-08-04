package com.bct.ngtpa.apiservice.adapter.in.web.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.adapter.in.web.auth.forgotpassword.dto.ForgotPasswordStartRequest;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.forgotpassword.mapper.ForgotPasswordCompleteWebMapper;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.forgotpassword.mapper.ForgotPasswordDetailsOtpWebMapper;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.forgotpassword.mapper.ForgotPasswordIdentityWebMapper;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.forgotpassword.mapper.ForgotPasswordStartWebMapper;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.registration.config.AuthFlowCookieProperties;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.registration.config.AuthFlowCookieWriter;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.registration.config.XsrfCookieWriter;
import com.bct.ngtpa.apiservice.application.port.in.StartForgotPasswordUseCase;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.start.StartForgotPasswordCommand;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.start.StartForgotPasswordError;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.start.StartForgotPasswordResult;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.start.StartForgotPasswordResultStatus;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpResponse;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class AuthForgotPasswordControllerTest {
    @Test
    void successfulStartWritesCookieAndReturnsCsrfOnly() {
        AtomicReference<StartForgotPasswordCommand> command = new AtomicReference<>();
        StartForgotPasswordUseCase useCase = value -> {
            command.set(value);
            return Mono.just(new StartForgotPasswordResult(
                    StartForgotPasswordResultStatus.STARTED,
                    com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowNextStep.ENTER_ID,
                    "csrf_secret",
                    "afs_secret",
                    List.of(),
                    null));
        };
        var controller = controller(
                useCase,
                new AuthFlowCookieWriter(new AuthFlowCookieProperties(
                        "auth_flow_sid", "/api/v1/auth", Duration.ofSeconds(900), true, true, "None")));
        var response = new MockServerHttpResponse();

        StepVerifier.create(controller.startForgotPassword(
                new ForgotPasswordStartRequest("Member01"),
                "fp_key_opaque",
                "req-1",
                "en",
                null,
                response))
                .assertNext(entity -> {
                    assertThat(entity.getStatusCode()).isEqualTo(HttpStatus.OK);
                    assertThat(entity.getBody().result().data().csrfToken()).isEqualTo("csrf_secret");
                    assertThat(entity.toString()).doesNotContain("afs_secret", "Member01");
                })
                .verifyComplete();

        assertThat(response.getCookies().getFirst("auth_flow_sid").getValue()).isEqualTo("afs_secret");
        assertThat(command.get().idempotencyKey().value()).isEqualTo("fp_key_opaque");
        assertThat(command.get().existingAuthFlowSessionId()).isNull();
    }

    @Test
    void usernameNotFoundMapsToBusinessRejectedWithoutCookieWrite() {
        StartForgotPasswordUseCase useCase = value -> Mono.just(new StartForgotPasswordResult(
                StartForgotPasswordResultStatus.USERNAME_NOT_FOUND,
                com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowNextStep.ENTER_USERNAME,
                null,
                null,
                List.of(new StartForgotPasswordError("err.forgotpassword.username.notFound", "username", null)),
                null));
        var controller = controller(useCase, AuthFlowCookieWriter.defaultWriter());
        var response = new MockServerHttpResponse();

        StepVerifier.create(controller.startForgotPassword(
                new ForgotPasswordStartRequest("Member01"),
                "fp_key_opaque",
                "req-1",
                "en",
                null,
                response))
                .assertNext(entity -> {
                    assertThat(entity.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
                    assertThat(entity.getBody().status())
                            .isEqualTo(com.bct.ngtpa.apiservice.adapter.in.web.response.ApiStatus.BUSINESS_REJECTED);
                    assertThat(entity.getBody().errors()).singleElement()
                            .extracting(error -> error.code())
                            .isEqualTo("err.forgotpassword.username.notFound");
                })
                .verifyComplete();

        assertThat(response.getCookies().getFirst("auth_flow_sid")).isNull();
    }

    private static AuthForgotPasswordController controller(
            StartForgotPasswordUseCase startUseCase,
            AuthFlowCookieWriter cookieWriter) {
        return new AuthForgotPasswordController(
                startUseCase,
                new ForgotPasswordStartWebMapper(Duration.ofMinutes(5)),
                command -> Mono.error(new AssertionError("identity use case must not be called")),
                new ForgotPasswordIdentityWebMapper(),
                command -> Mono.error(new AssertionError("details use case must not be called")),
                new ForgotPasswordDetailsOtpWebMapper(),
                command -> Mono.error(new AssertionError("complete use case must not be called")),
                new ForgotPasswordCompleteWebMapper(),
                cookieWriter,
                new XsrfCookieWriter(new com.bct.ngtpa.apiservice.adapter.in.web.session.config.AuthFlowSessionProperties()));
    }
}
