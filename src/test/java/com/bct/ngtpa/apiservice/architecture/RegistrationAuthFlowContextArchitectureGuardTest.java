package com.bct.ngtpa.apiservice.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class RegistrationAuthFlowContextArchitectureGuardTest {

    @Test
    void applicationContextModelsHaveNoSpringRedisApimOrWebDependencies() throws Exception {
        Path root = Path.of("src/main/java/com/bct/ngtpa/apiservice/application/dto/auth/registration/context");
        if (!Files.exists(root)) {
            return;
        }
        try (var files = Files.walk(root)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                String source = Files.readString(file);
                assertThat(source).doesNotContain(
                        "org.springframework", "jakarta.servlet", "adapter.", "lettuce", "redis", "org.slf4j");
            }
        }
    }

    @Test
    void p7_1DoesNotIntroduceOtpResendVerifyEndpointsYet() throws Exception {
        String allControllers = "";
        Path controllerRoot = Path.of("src/main/java/com/bct/ngtpa/apiservice/adapter/in/web/controller");
        if (Files.exists(controllerRoot)) {
            try (var files = Files.walk(controllerRoot)) {
                allControllers = String.join("\n", files.filter(path -> path.toString().endsWith(".java"))
                        .map(path -> {
                            try {
                                return Files.readString(path);
                            } catch (Exception exception) {
                                return "";
                            }
                        }).toList());
            }
        }
        assertThat(allControllers).doesNotContain(
                "/api/v1/auth/registration/otp/resend",
                "/api/v1/auth/registration/otp/verify",
                "/api/v1/auth/registration/username/check",
                "/api/v1/auth/registration/complete",
                "/api/v1/auth/registration/cancel",
                "/api/v1/auth/flow/resume",
                "/api/v1/auth/captcha/challenge");
    }
}
