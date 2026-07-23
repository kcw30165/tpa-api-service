package com.bct.ngtpa.apiservice.architecture;

import static org.assertj.core.api.Assertions.assertThat;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class Task0043LoginStartReplayArchitectureTest {
        @Test
        void applicationReplayPortHasNoRedisSpringOrCryptoDependency() throws Exception {
                String source = Files.readString(Path.of(
                                "src/main/java/com/bct/ngtpa/apiservice/application/port/out/LoginStartReplayPort.java"));
                assertThat(source).doesNotContain(
                                "org.springframework", "Redis", "ObjectMapper", "Cipher", "adapter.out");
        }

        @Test
        void idempotencySnapshotContainsOnlyOpaqueCommitReference() throws Exception {
                String source = Files.readString(Path.of(
                                "src/main/java/com/bct/ngtpa/apiservice/application/dto/auth/login/idempotency/LoginIdempotencyReplaySnapshot.java"));
                assertThat(source).contains("outcomeCode", "opaqueCommitRef");
                assertThat(source).doesNotContain(
                                "authFlowSessionId", "csrfToken", "destinations", "ciphertext", "password", "username");
        }

        @Test
        void replayRedisKeyContainsNoSessionCsrfOrIdentityData() throws Exception {
                String source = Files.readString(Path.of(
                                "src/main/java/com/bct/ngtpa/apiservice/adapter/out/redis/auth/login/replay/RedisLoginStartReplayKeyFactory.java"));
                assertThat(source).contains(":auth:login:start-replay:");
                assertThat(source).doesNotContain(
                                "username", "password", "sessionId", "csrf", "destination");
        }

        @Test
        void replayCompositionLivesOutsideApplicationLayer() throws Exception {
                String source = Files.readString(Path.of(
                                "src/main/java/com/bct/ngtpa/apiservice/config/auth/login/LoginStartReplayConfig.java"));
                assertThat(source).contains("@Configuration", "LoginStartReplayPort");
        }
}
