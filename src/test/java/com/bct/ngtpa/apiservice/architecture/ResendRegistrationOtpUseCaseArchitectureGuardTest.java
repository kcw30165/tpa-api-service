package com.bct.ngtpa.apiservice.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ResendRegistrationOtpUseCaseArchitectureGuardTest {

    @Test
    void resendOtpUseCaseHasNoWebRedisApimAdapterOrCookieDependencies() throws Exception {
        Path root = Path.of("src/main/java/com/bct/ngtpa/apiservice/application/usecase/auth/registration/otp");
        if (!Files.exists(root)) {
            return;
        }
        try (var files = Files.walk(root)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                String source = Files.readString(file);
                assertThat(source).doesNotContain(
                        "adapter.in.web", "adapter.out.redis", "adapter.out.apim", "adapter.out.sms", "adapter.out.email",
                        "org.springframework", "jakarta.servlet", "ServerWebExchange", "ServerHttpRequest", "ServerHttpResponse",
                        "ReactiveRedisTemplate", "Cookie");
            }
        }
    }

    @Test
    void p7_2DoesNotIntroduceControllerOrEndpoint() throws Exception {
        String sources = "";
        try (var files = Files.walk(Path.of("src/main/java"))) {
            sources = String.join("\n", files.filter(path -> path.toString().endsWith(".java"))
                    .map(path -> {
                        try { return Files.readString(path); } catch (Exception exception) { return ""; }
                    }).toList());
        }
        assertThat(sources).doesNotContain(
                "AuthRegistrationOtpController",
                "/api/v1/auth/registration/otp/resend",
                "/api/v1/auth/registration/otp/verify",
                "VerifyRegistrationOtpUseCase",
                "CheckRegistrationUsernameUseCase",
                "CompleteRegistrationUseCase",
                "CancelRegistrationUseCase",
                "AuthFlowResumeRequest",
                "ResumeAuthFlowUseCase",
                "AuthCaptchaController",
                "CaptchaChallengePort");
    }
}
