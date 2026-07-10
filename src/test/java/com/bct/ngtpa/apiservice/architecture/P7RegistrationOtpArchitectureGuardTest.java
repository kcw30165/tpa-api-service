package com.bct.ngtpa.apiservice.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class P7RegistrationOtpArchitectureGuardTest {

    @Test
    void p7UseCasesRemainFrameworkFreeAndAdaptersDoNotLeakInward() throws Exception {
        Path root = Path.of("src/main/java/com/bct/ngtpa/apiservice/application/usecase/auth/registration/otp");
        if (!Files.exists(root)) {
            return;
        }
        try (var files = Files.walk(root)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                String source = Files.readString(file);
                assertThat(source).doesNotContain(
                        "org.springframework", "org.springframework.http", "org.springframework.web", "jakarta.servlet",
                        "adapter.in.web", "adapter.out.redis", "adapter.out.apim", "adapter.out.sms",
                        "adapter.out.email",
                        "ReactiveRedisTemplate", "ServerWebExchange", "ServerHttpRequest", "ServerHttpResponse",
                        "Cookie");
            }
        }
    }

    @Test
    void p7ControllersDoNotDependOnOutboundAdapters() throws Exception {
        for (Path controller : new Path[] {
                Path.of("src/main/java/com/bct/ngtpa/apiservice/adapter/in/web/controller/AuthRegistrationOtpController.java"),
                Path.of("src/main/java/com/bct/ngtpa/apiservice/adapter/in/web/controller/AuthRegistrationOtpVerifyController.java") }) {
            if (!Files.exists(controller)) {
                continue;
            }
            String source = Files.readString(controller);
            assertThat(source).doesNotContain(
                    "adapter.out.redis", "adapter.out.apim", "adapter.out.sms", "adapter.out.email",
                    "ApimWebClientFacade", "ReactiveRedisTemplate", "OtpChallengePort", "MessageDeliveryPort",
                    "RegistrationLookupPort");
        }
    }

    @Test
    void p7UsesExistingRegistrationLookupPortAndDoesNotIntroduceFutureScope() throws Exception {
        String sources;
        try (var files = Files.walk(Path.of("src/main/java"))) {
            sources = String.join("\n", files.filter(path -> path.toString().endsWith(".java"))
                    .map(path -> {
                        try {
                            return Files.readString(path);
                        } catch (Exception exception) {
                            return "";
                        }
                    })
                    .toList());
        }
        assertThat(sources).contains("RegistrationLookupPort");
        assertThat(sources).doesNotContain(
                "/api/v1/auth/registration/complete",
                "/api/v1/auth/registration/cancel",
                "/api/v1/auth/flow/resume",
                "CompleteRegistrationUseCase",
                "CancelRegistrationUseCase",
                "AuthFlowResumeRequest",
                "ResumeAuthFlowUseCase");
    }
}
