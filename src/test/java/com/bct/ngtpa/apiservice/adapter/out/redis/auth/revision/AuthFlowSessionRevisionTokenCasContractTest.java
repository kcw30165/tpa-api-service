package com.bct.ngtpa.apiservice.adapter.out.redis.auth.revision;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class AuthFlowSessionRevisionTokenCasContractTest {
  private static final Path REPLACE =
      Path.of("src/main/resources/redis/auth-flow-session/replace-revisioned.lua");
  private static final Path DELETE =
      Path.of("src/main/resources/redis/auth-flow-session/delete-revisioned.lua");
  private static final Path LEGACY =
      Path.of("src/main/resources/redis/auth-flow-session/initialize-legacy-revision.lua");
  private static final Path ADAPTER =
      Path.of(
          "src/main/java/com/bct/ngtpa/apiservice/adapter/out/redis/auth/RedisAuthFlowSessionAdapter.java");

  @Test
  void replaceUsesSessionRevisionAndPreservesRemainingTtl() throws Exception {
    String script = Files.readString(REPLACE);
    assertThat(script)
        .contains("pcall(cjson.decode, current)")
        .contains("decoded['sessionRevision']")
        .contains("currentRevision ~= ARGV[1]")
        .contains("redis.call('PTTL', KEYS[1])")
        .contains("redis.call('PSETEX', KEYS[1], ttl, ARGV[2])")
        .doesNotContain("current ~= ARGV[1]");
  }

  @Test
  void deleteUsesSessionRevisionRatherThanWholeDocument() throws Exception {
    String script = Files.readString(DELETE);
    assertThat(script)
        .contains("pcall(cjson.decode, current)")
        .contains("decoded['sessionRevision']")
        .contains("currentRevision ~= ARGV[1]")
        .contains("redis.call('DEL', KEYS[1])")
        .doesNotContain("current ~= ARGV[1]");
  }

  @Test
  void legacyInitializationRetainsWholeDocumentProofBecauseNoRevisionExistsYet() throws Exception {
    assertThat(Files.readString(LEGACY))
        .contains("current ~= ARGV[2]")
        .contains("redis.call('PTTL', KEYS[1])");
  }

  @Test
  void adapterPassesExpectedRevisionAndMapsMissingRevision() throws Exception {
    String source = Files.readString(ADAPTER);
    assertThat(source)
        .contains("command.expected().revision().value()")
        .contains("case -2 -> AuthFlowSessionMutationOutcome.SESSION_REVISION_MISSING")
        .doesNotContain("diagnoseWholeValueConflict")
        .doesNotContain("cas-conflict-diagnostic");
  }
}
