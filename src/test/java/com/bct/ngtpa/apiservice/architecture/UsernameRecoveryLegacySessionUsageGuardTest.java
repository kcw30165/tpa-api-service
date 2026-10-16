package com.bct.ngtpa.apiservice.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Repair 025D-2 closure guard for the removed legacy Username Recovery session stack.
 *
 * <p>The canonical runtime is the generic auth-flow envelope plus the typed Username Recovery
 * workflow-state sidecar. The legacy aggregate, ports, Redis adapter, mapper, record, key factory,
 * and Lua resource directory must not return to production source or resources.
 */
class UsernameRecoveryLegacySessionUsageGuardTest {

  private static final List<String> REMOVED_PRODUCTION_PATHS =
      List.of(
          "src/main/java/com/bct/ngtpa/apiservice/adapter/out/redis/usernamerecovery/RedisUsernameRecoverySessionAdapter.java",
          "src/main/java/com/bct/ngtpa/apiservice/adapter/out/redis/usernamerecovery/RedisUsernameRecoverySessionJsonMapper.java",
          "src/main/java/com/bct/ngtpa/apiservice/adapter/out/redis/usernamerecovery/RedisUsernameRecoverySessionKeyFactory.java",
          "src/main/java/com/bct/ngtpa/apiservice/adapter/out/redis/usernamerecovery/RedisUsernameRecoverySessionRecord.java",
          "src/main/java/com/bct/ngtpa/apiservice/application/port/out/UsernameRecoverySessionDeleteResult.java",
          "src/main/java/com/bct/ngtpa/apiservice/application/port/out/UsernameRecoverySessionLoadResult.java",
          "src/main/java/com/bct/ngtpa/apiservice/application/port/out/UsernameRecoverySessionMutationCommand.java",
          "src/main/java/com/bct/ngtpa/apiservice/application/port/out/UsernameRecoverySessionMutationOutcome.java",
          "src/main/java/com/bct/ngtpa/apiservice/application/port/out/UsernameRecoverySessionMutationResult.java",
          "src/main/java/com/bct/ngtpa/apiservice/application/port/out/UsernameRecoverySessionPort.java",
          "src/main/java/com/bct/ngtpa/apiservice/domain/model/UsernameRecoverySession.java",
          "src/main/java/com/bct/ngtpa/apiservice/domain/model/UsernameRecoverySessionId.java",
          "src/main/resources/redis/username-recovery-session/update.lua",
          "src/main/resources/redis/username-recovery-session/delete.lua");

  @Test
  void legacyUsernameRecoverySessionStackIsPhysicallyAbsent() {
    assertThat(REMOVED_PRODUCTION_PATHS)
        .isNotEmpty()
        .allSatisfy(
            path ->
                assertThat(Files.exists(Path.of(path)))
                    .as("removed legacy path %s", path)
                    .isFalse());
  }
}
