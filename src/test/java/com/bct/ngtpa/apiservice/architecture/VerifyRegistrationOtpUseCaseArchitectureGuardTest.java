package com.bct.ngtpa.apiservice.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class VerifyRegistrationOtpUseCaseArchitectureGuardTest {
    @Test
    void verifyOtpUseCaseHasNoWebRedisApimAdapterOrCookieDependencies() throws Exception {
        Path root = Path.of("src/main/java/com/bct/ngtpa/apiservice/application/usecase/auth/registration/otp");
        if (!Files.exists(root)) { return; }
        try (var files = Files.walk(root)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                String source = Files.readString(file);
                assertThat(source).doesNotContain("adapter.in.web", "adapter.out.redis", "adapter.out.apim", "adapter.out.sms", "adapter.out.email", "org.springframework", "ServerWebExchange", "ServerHttpRequest", "ServerHttpResponse", "Cookie", "ReactiveRedisTemplate");
            }
        }
    }
}
