package com.bct.ngtpa.apiservice.adapter.out.redis.auth;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class P3RedisKeySafetyGuardTest {

    @Test
    void p3RedisRateLimitKeyUsesAuthRateNamespace() throws Exception {
        String source = redisRateLimitSources();

        assertThat(source).contains("keyFactory.key");
        assertThat(source).contains("\"auth\"");
        assertThat(source).contains("\"rate\"");
        assertThat(source).contains("command.bucket().name()");
        assertThat(source).contains("command.bucketId()");
        assertThat(source).doesNotContain("member@example.com")
                .doesNotContain("91234567")
                .doesNotContain("csrf_")
                .doesNotContain("reg_sid")
                .doesNotContain("password");
    }

    private String redisRateLimitSources() throws Exception {
        Path adapter = Path.of("src/main/java/com/bct/ngtpa/apiservice/adapter/out/redis/auth/RedisRateLimitAdapter.java");
        return Files.exists(adapter) ? Files.readString(adapter) : "";
    }
}
