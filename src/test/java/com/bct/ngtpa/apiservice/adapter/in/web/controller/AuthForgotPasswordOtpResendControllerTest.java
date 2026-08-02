package com.bct.ngtpa.apiservice.adapter.in.web.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.adapter.in.web.auth.forgotpassword.mapper.ForgotPasswordOtpResendWebMapper;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.forgotpassword.mapper.ForgotPasswordOtpVerifyWebMapper;
import com.bct.ngtpa.apiservice.application.port.in.ResendForgotPasswordOtpUseCase;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.otp.resend.ResendForgotPasswordOtpCommand;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.otp.resend.ResendForgotPasswordOtpResult;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.otp.resend.ResendForgotPasswordOtpStatus;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpResponse;
import org.springframework.web.bind.annotation.PostMapping;
import reactor.core.publisher.Mono;

class AuthForgotPasswordOtpResendControllerTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void otpResendEndpointExists() throws Exception {
        Method method = AuthForgotPasswordOtpController.class.getDeclaredMethod(
                "resend",
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
        assertThat(mapping.path()).isEmpty();
        assertThat(mapping.value()).contains("/resend");
    }

    @Test
    void otpResendRequiresAuthFlowCookie() throws Exception {
        var useCase = new RecordingUseCase();
        var controller = controller(useCase);

        String json = objectMapper.writeValueAsString(controller
                .resend(null, "csrf", null, null, "req", "en", "idem", new MockServerHttpResponse())
                .block());

        assertThat(json).contains("err.system.unavailable");
        assertThat(useCase.called).isFalse();
    }

    @Test
    void otpResendRequiresXsrfTokenHeader() throws Exception {
        var useCase = new RecordingUseCase();
        var controller = controller(useCase);

        String json = objectMapper.writeValueAsString(controller
                .resend("afs", null, null, null, "req", "en", "idem", new MockServerHttpResponse())
                .block());

        assertThat(json).contains("err.system.unavailable");
        assertThat(useCase.called).isFalse();
    }

    @Test
    void otpResendRequiresIdempotencyKey() throws Exception {
        var useCase = new RecordingUseCase();
        var controller = controller(useCase);

        String json = objectMapper.writeValueAsString(controller
                .resend("afs", "csrf", null, null, "req", "en", null, new MockServerHttpResponse())
                .block());

        assertThat(json).contains("err.workflow.idempotency.missing");
        assertThat(useCase.called).isFalse();
    }

    @Test
    void otpResendMapsHeadersAndCookieToUseCaseCommand() {
        var useCase = new RecordingUseCase();
        var controller = controller(useCase);
        var response = new MockServerHttpResponse();

        controller.resend("afs_abc", "csrf_abc", "https://portal.example", "https://portal.example/page",
                "req_abc", "zh-HK", "idem_abc", response).block();

        assertThat(useCase.called).isTrue();
        assertThat(useCase.command.sessionId().value()).isEqualTo("afs_abc");
        assertThat(useCase.command.csrfToken()).isEqualTo("csrf_abc");
        assertThat(useCase.command.origin()).isEqualTo("https://portal.example");
        assertThat(useCase.command.referer()).isEqualTo("https://portal.example/page");
        assertThat(useCase.command.requestId()).isEqualTo("req_abc");
        assertThat(useCase.command.language()).isEqualTo("zh-HK");
        assertThat(useCase.command.idempotencyKey()).isEqualTo("idem_abc");
        assertThat(response.getHeaders().getFirst("X-Request-Id")).isEqualTo("req_abc");
    }

    @Test
    void otpResendSuccessReturnsVerifyOtpPayloadWithoutSensitiveFields() {
        var controller = controller(new RecordingUseCase());

        JsonNode json = objectMapper.valueToTree(controller
                .resend("afs_abc", "csrf_abc", null, null, "req_abc", "en", "idem_abc",
                        new MockServerHttpResponse())
                .block());

        assertThat(json.at("/body/result/nextStep").asText()).isEqualTo("VERIFY_OTP");
        assertThat(json.at("/body/result/data/maskedDestination").asText()).isEqualTo("m****r@example.com");
        assertThat(json.at("/body/result/data/expiresInSeconds").asInt()).isEqualTo(300);
        assertThat(json.at("/body/result/data/resendAvailableInSeconds").asInt()).isEqualTo(60);
        assertThat(json.toString()).doesNotContain(
                "afs_abc",
                "csrf_abc",
                "idem_abc",
                "otpChallengeId",
                "activeOtpChallengeId",
                "XSRF-TOKEN",
                "auth_flow_sid",
                "123456");
    }

    private static final class RecordingUseCase implements ResendForgotPasswordOtpUseCase {
        boolean called;
        ResendForgotPasswordOtpCommand command;

        @Override
        public Mono<ResendForgotPasswordOtpResult> resend(ResendForgotPasswordOtpCommand command) {
            called = true;
            this.command = command;
            return Mono.just(new ResendForgotPasswordOtpResult(
                    ResendForgotPasswordOtpStatus.RESENT,
                    "m****r@example.com",
                    300,
                    60));
        }
    }

    private static AuthForgotPasswordOtpController controller(ResendForgotPasswordOtpUseCase resendUseCase) {
        return new AuthForgotPasswordOtpController(
                resendUseCase,
                new ForgotPasswordOtpResendWebMapper(),
                command -> Mono.error(new AssertionError("verify use case must not be called")),
                new ForgotPasswordOtpVerifyWebMapper());
    }
}