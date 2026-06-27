package com.bct.ngtpa.apiservice.adapter.out.redis.auth;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

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
        Path root = Path.of("src/main/java/com/bct/ngtpa/apiservice/adapter/out/redis");
        if (!Files.exists(root)) {
            return "";
        }
        StringBuilder combined = new StringBuilder();
        try (Stream<Path> paths = Files.walk(root)) {
            paths.filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> {
                        String name = path.getFileName().toString().toLowerCase();
                        return name.contains("ratelimit") || name.contains("rate") || name.contains("key");
                    })
                    .forEach(path -> {
                        try {
                            combined.append(Files.readString(path)).append('\n');
                        } catch (Exception exception) {
                            throw new IllegalStateException(exception);
                        }
                    });
        }
        return combined.toString();
    }
}
