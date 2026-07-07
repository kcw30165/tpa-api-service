package com.bct.ngtpa.apiservice.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class AuthRegistrationOtpVerifyControllerArchitectureGuardTest {

    @Test
    void otpVerifyControllerDoesNotDependOnOutboundAdapters() throws Exception {
        Path controller = Path.of("src/main/java/com/bct/ngtpa/apiservice/adapter/in/web/controller/AuthRegistrationOtpVerifyController.java");
        if (!Files.exists(controller)) {
            return;
        }
        String source = Files.readString(controller);
        assertThat(source).doesNotContain(
                "adapter.out.redis", "adapter.out.apim", "adapter.out.sms", "adapter.out.email",
                "ApimWebClientFacade", "ReactiveRedisTemplate", "OtpChallengePort", "MessageDeliveryPort", "RegistrationLookupPort");
    }

    @Test
    void p7_5DoesNotIntroduceUsernameCompleteCancelOrResume() throws Exception {
        String sources;
        try (var files = Files.walk(Path.of("src/main/java"))) {
            sources = String.join("\n", files.filter(path -> path.toString().endsWith(".java"))
                    .map(path -> {
                        try { return Files.readString(path); } catch (Exception exception) { return ""; }
                    }).toList());
        }
        assertThat(sources).doesNotContain(
                "/api/v1/auth/registration/username/check",
                "/api/v1/auth/registration/complete",
                "/api/v1/auth/registration/cancel",
                "/api/v1/auth/flow/resume",
                "AuthFlowResumeRequest",
            "ResumeAuthFlowUseCase");
    }
}
