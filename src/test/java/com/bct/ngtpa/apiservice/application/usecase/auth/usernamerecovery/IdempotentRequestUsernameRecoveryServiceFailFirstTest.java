package com.bct.ngtpa.apiservice.application.usecase.auth.usernamerecovery;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Fail-first ownership boundary for the P6-D shared REQUEST coordinator. */
class IdempotentRequestUsernameRecoveryServiceFailFirstTest {
  private static final Path COORDINATOR =
      Path.of(
          "src/main/java/com/bct/ngtpa/apiservice/application/usecase/auth/usernamerecovery/IdempotentRequestUsernameRecoveryService.java");
  private static final Path CONFIG =
      Path.of("src/main/java/com/bct/ngtpa/apiservice/config/UsernameRecoveryFeatureConfig.java");

  @Test
  void sharedRequestCoordinatorExists() {
    assertThat(COORDINATOR).exists();
  }

  @Test
  void coordinatorUsesOnlyTheSharedLifecycleAndRequestOwnedPolicies() throws Exception {
    String source = Files.readString(COORDINATOR);
    assertThat(source)
        .contains(
            "IdempotencyExecutor",
            "UsernameRecoveryIdempotencyIntegrationPolicy",
            "UsernameRecoveryIdempotencyAction.REQUEST",
            "UsernameRecoveryRequestProtectedOperation",
            "UsernameRecoveryRequestExecutionBridge",
            "IdempotentExecutionCommand")
        .doesNotContain(
            "UsernameRecoveryIdempotencyPort",
            "UsernameRecoveryIdempotencyRecord",
            "UsernameRecoveryRequestFingerprintGenerator",
            "UsernameRecoveryRequestReplayCodec.encode(",
            "Redis",
            "org.springframework");
  }

  @Test
  void onlyAcquiredExecutionInvokesTheProtectedOperationAndUsesTypedFailureClassification()
      throws Exception {
    String source = Files.readString(COORDINATOR).replaceAll("\\s+", "");
    assertThat(source)
        .contains("executor.execute(")
        .contains("protectedOperation.execute(")
        .contains("bridge::capture")
        .contains("bridge::hydrate")
        .contains("FailureBoundary.PROVEN_PRE_DISPATCH")
        .contains("policy.possibleSideEffect().classify(error)");
  }

  @Test
  void lifecycleOutcomesRemainInternalAndNeverBecomePublicCompletedReplay() throws Exception {
    String source = Files.readString(COORDINATOR);
    assertThat(source)
        .contains(
            "ACQUIRED",
            "COMPLETED_REPLAY",
            "DUPLICATE_IN_PROGRESS",
            "KEY_REUSED_WITH_DIFFERENT_REQUEST",
            "FAILED_EXISTING",
            "UNKNOWN_OUTCOME_EXISTING")
        .doesNotContain("UsernameRecoveryRequestResult.Outcome.COMPLETED_REPLAY");
  }

  @Test
  void productionRequestRemainsLegacyUntilDedicatedAtomicSwitch() throws Exception {
    String source = Files.readString(CONFIG);
    assertThat(source)
        .contains("return new IdempotentStartUsernameRecoveryService(")
        .contains("return new IdempotentRequestUsernameRecoveryService(")
        .contains("return new CancelUsernameRecoveryService(")
        .doesNotContain("return new RequestUsernameRecoveryService(");
  }
}
