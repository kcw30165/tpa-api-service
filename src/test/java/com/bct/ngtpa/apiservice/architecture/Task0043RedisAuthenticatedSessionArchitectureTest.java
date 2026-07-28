package com.bct.ngtpa.apiservice.architecture;

import static org.assertj.core.api.Assertions.assertThat;
import java.nio.file.*;
import org.junit.jupiter.api.Test;

class Task0043RedisAuthenticatedSessionArchitectureTest {
    @Test
    void adapterStoresOnlyProtectedEnvelope() throws Exception {
        String s = Files.readString(Path.of(
                "src/main/java/com/bct/ngtpa/apiservice/adapter/out/redis/auth/RedisAuthenticatedSessionAdapter.java"));
        assertThat(s)
                .contains("AuthenticatedSessionPort", "ProtectedAuthenticatedSession", "auth:authenticated-session")
                .doesNotContain("ActorContext", "AuthenticatedSessionAccount", "policyNo", "certNo", "csrfTokenHash");
    }

    @Test
    void applicationDoesNotDependOnRedisAdapter() throws Exception {
        String all = Files.walk(Path.of("src/main/java/com/bct/ngtpa/apiservice/application"))
                .filter(Files::isRegularFile).map(p -> {
                    try {
                        return Files.readString(p);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }).reduce("", String::concat);
        assertThat(all).doesNotContain("adapter.out.redis.auth.RedisAuthenticatedSessionAdapter",
                "ReactiveStringRedisTemplate");
    }
}
