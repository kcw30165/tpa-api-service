package com.bct.ngtpa.apiservice.architecture;

import static org.assertj.core.api.Assertions.assertThat;
import java.nio.file.*;
import org.junit.jupiter.api.Test;

class Task0043LoginOtpSendRedisIdempotencyArchitectureTest {
    @Test
    void keyUsesDedicatedOpaqueNamespace() throws Exception {
        String s = Files.readString(Path.of(
                "src/main/java/com/bct/ngtpa/apiservice/adapter/out/redis/auth/login/otp/RedisLoginOtpSendIdempotencyKeyFactory.java"));
        assertThat(s).contains(":auth:idempotency:login:otp-send:");
        assertThat(s).doesNotContain("sessionId", "destinationId", "csrfToken", "requestHash");
    }

    @Test
    void adapterUsesAtomicSetIfAbsentAndSafeWireFields() throws Exception {
        String s = Files.readString(Path.of(
                "src/main/java/com/bct/ngtpa/apiservice/adapter/out/redis/auth/login/otp/RedisLoginOtpSendIdempotencyAdapter.java"));
        assertThat(s).contains("setIfAbsent", "requestHash", "replay");
        assertThat(s).doesNotContain("rawCode", "otpCode", "destinationId", "sessionId", "csrfToken", "provider");
    }

    @Test
    void springCompositionUsesConfiguredOtpSendTtlOwner() throws Exception {
        String s = Files.readString(
                Path.of("src/main/java/com/bct/ngtpa/apiservice/config/auth/login/LoginOtpSendIdempotencyConfig.java"));
        assertThat(s).contains("getOtpSendTtlSeconds", "LoginOtpSendIdempotencyPort");
    }
}
