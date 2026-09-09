package com.bct.ngtpa.apiservice.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class UsernameRecoveryCancelSharedMigrationFailFirstTest {
  private static final Path ROOT = Path.of("src/main/java/com/bct/ngtpa/apiservice");
  private static final Path CONFIG = ROOT.resolve("config/UsernameRecoveryFeatureConfig.java");
  private static final Path COORDINATOR =
      ROOT.resolve(
          "application/usecase/auth/usernamerecovery/IdempotentCancelUsernameRecoveryService.java");
  private static final Path OPERATION =
      ROOT.resolve(
          "application/usecase/auth/usernamerecovery/UsernameRecoveryCancelProtectedOperation.java");
  private static final Path RESULT =
      ROOT.resolve(
          "application/usecase/auth/usernamerecovery/UsernameRecoveryCancelProtectedExecutionResult.java");
  private static final Path BRIDGE =
      ROOT.resolve(
          "application/usecase/auth/usernamerecovery/idempotency/cancel/UsernameRecoveryCancelExecutionBridge.java");
  private static final Path CODEC =
      ROOT.resolve(
          "application/usecase/auth/usernamerecovery/idempotency/cancel/UsernameRecoveryCancelReplayCodec.java");

  @Test
  void requiresCompleteSharedCancelAssetBoundary() {
    assertThat(COORDINATOR).exists();
    assertThat(OPERATION).exists();
    assertThat(RESULT).exists();
    assertThat(BRIDGE).exists();
    assertThat(CODEC).exists();
  }

  @Test
  void requiresThinCoordinatorOverSharedExecutor() throws Exception {
    assertThat(COORDINATOR).exists();
    String source = Files.readString(COORDINATOR).replaceAll("\\s+", "");
    assertThat(source)
        .contains("IdempotencyExecutor")
        .contains("policy.operationId(UsernameRecoveryIdempotencyAction.CANCEL")
        .contains("policy.cancel(")
        .contains("operation.execute(")
        .contains("bridge::capture")
        .contains("bridge::hydrate")
        .doesNotContain("UsernameRecoveryIdempotencyPort")
        .doesNotContain("RedisUsernameRecoveryIdempotencyAdapter");
  }

  @Test
  void requiresLifecycleNeutralRevisionAwareProtectedOperation() throws Exception {
    assertThat(OPERATION).exists();
    String source = Files.readString(OPERATION).replaceAll("\\s+", "");
    assertThat(source)
        .contains("CsrfTokenValidationService")
        .contains("UsernameRecoveryAuthFlowContextBinding")
        .contains("AuthFlowSessionPort")
        .contains("revision")
        .doesNotContain("IdempotencyExecutor")
        .doesNotContain("UsernameRecoveryIdempotencyPort")
        .doesNotContain(".reserve(")
        .doesNotContain(".complete(")
        .doesNotContain("recordUnknownOutcome(");
  }

  @Test
  void requiresStrictSafeReplayWithoutProtectedStore() throws Exception {
    assertThat(BRIDGE).exists();
    assertThat(CODEC).exists();
    String bridge = Files.readString(BRIDGE).replaceAll("\\s+", "");
    String codec = Files.readString(CODEC).replaceAll("\\s+", "");
    assertThat(bridge).contains("capture(").contains("hydrate(");
    assertThat(codec)
        .contains("UsernameRecoveryIdempotencyAction.CANCEL")
        .doesNotContain("ProtectedReplayPort")
        .doesNotContain("AuthFlowSessionPort")
        .doesNotContain("rawCsrfToken");
  }

  @Test
  void keepsProductionLegacyUntilDedicatedAtomicSwitch() throws Exception {
    String source = Files.readString(CONFIG).replaceAll("\\s+", "");
    assertThat(source)
        .contains("returnnewIdempotentCancelUsernameRecoveryService(")
        .doesNotContain("returnnewCancelUsernameRecoveryService(")
        .contains("returnnewIdempotentStartUsernameRecoveryService(")
        .contains("returnnewIdempotentRequestUsernameRecoveryService(");
  }
}
