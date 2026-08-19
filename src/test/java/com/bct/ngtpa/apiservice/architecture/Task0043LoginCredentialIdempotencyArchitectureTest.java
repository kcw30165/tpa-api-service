package com.bct.ngtpa.apiservice.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.*;
import org.junit.jupiter.api.Test;

class Task0043LoginCredentialIdempotencyArchitectureTest {
  @Test
  void loginPortReusesGenericAuthFlowLifecycle() throws Exception {
    String source =
        Files.readString(
            Path.of(
                "src/main/java/com/bct/ngtpa/apiservice/application/port/out/LoginCredentialIdempotencyPort.java"));
    assertThat(source).contains("AuthFlowIdempotencyPort");
  }

  @Test
  void redisKeyContainsOnlyPrefixActionAndOpaqueIdempotencyKey() throws Exception {
    String source =
        Files.readString(
            Path.of(
                "src/main/java/com/bct/ngtpa/apiservice/adapter/out/redis/auth/login/RedisLoginIdempotencyKeyFactory.java"));
    assertThat(source).contains(":auth:idempotency:login:");
    assertThat(source).doesNotContain("username", "password", "sessionId", "destination");
  }

  @Test
  void replaySnapshotCannotCarryCookiesCsrfSessionOrCredentials() throws Exception {
    String source =
        Files.readString(
            Path.of(
                "src/main/java/com/bct/ngtpa/apiservice/application/dto/auth/login/idempotency/LoginIdempotencyReplaySnapshot.java"));
    assertThat(source).contains("outcomeCode", "opaqueCommitRef");
    assertThat(source)
        .doesNotContain("cookie", "csrf", "password", "username", "sessionId", "destination");
  }
}
