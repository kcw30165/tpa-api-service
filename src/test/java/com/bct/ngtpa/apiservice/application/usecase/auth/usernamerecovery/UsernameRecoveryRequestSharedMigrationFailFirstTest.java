package com.bct.ngtpa.apiservice.application.usecase.auth.usernamerecovery;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Fail-first boundary for the P6-D REQUEST migration to repository-wide shared idempotency. */
class UsernameRecoveryRequestSharedMigrationFailFirstTest {
  private static final Path ROOT = Path.of("src/main/java/com/bct/ngtpa/apiservice");
  private static final Path COORDINATOR =
      ROOT.resolve(
          "application/usecase/auth/usernamerecovery/IdempotentRequestUsernameRecoveryService.java");
  private static final Path OPERATION =
      ROOT.resolve(
          "application/usecase/auth/usernamerecovery/UsernameRecoveryRequestProtectedOperation.java");
  private static final Path RESULT =
      ROOT.resolve(
          "application/usecase/auth/usernamerecovery/UsernameRecoveryRequestProtectedExecutionResult.java");
  private static final Path BRIDGE =
      ROOT.resolve(
          "application/usecase/auth/usernamerecovery/idempotency/request/UsernameRecoveryRequestExecutionBridge.java");
  private static final Path CODEC =
      ROOT.resolve(
          "application/usecase/auth/usernamerecovery/idempotency/request/UsernameRecoveryRequestReplayCodec.java");
  private static final Path CONFIG = ROOT.resolve("config/UsernameRecoveryFeatureConfig.java");

  @Test
  void requiredSharedRequestAssetsExist() {
    assertThat(COORDINATOR).exists();
    assertThat(OPERATION).exists();
    assertThat(RESULT).exists();
    assertThat(BRIDGE).exists();
    assertThat(CODEC).exists();
  }

  @Test
  void protectedOperationOwnsDeliveryAndSessionMutationButNoIdempotencyLifecycle()
      throws Exception {
    String source = Files.readString(OPERATION).replaceAll("\\s+", "");
    assertThat(source)
        .contains("MessageDeliveryPort", "messageDeliveryPort", ".deliver(delivery)")
        .contains("AuthFlowSessionPort", "updateRevisioned(")
        .contains("markRequested(", "markFailed(", "markUnknownOutcome(")
        .doesNotContain(
            "IdempotencyExecutor",
            "IdempotencyPort",
            "idempotencyPort.reserve(",
            "idempotencyPort.complete(",
            "recordUnknownOutcome(");
  }

  @Test
  void sharedCoordinatorAloneOwnsSharedLifecycleAndConservativeDispatchClassification()
      throws Exception {
    String source = Files.readString(COORDINATOR).replaceAll("\\s+", "");
    assertThat(source)
        .contains("IdempotencyExecutor", "UsernameRecoveryIdempotencyIntegrationPolicy")
        .contains("UsernameRecoveryIdempotencyAction.REQUEST")
        .contains("policy.request(")
        .contains("policy.possibleSideEffect()")
        .contains("protectedOperation.execute(")
        .contains("bridge::capture", "bridge::hydrate")
        .doesNotContain("UsernameRecoveryIdempotencyPort", "messageDeliveryPort.deliver(");
  }

  @Test
  void replayCodecPersistsOnlyPublicResultAndNeverDirectProtectedIdentifiers() throws Exception {
    String source = Files.readString(CODEC);
    assertThat(source)
        .contains("UsernameRecoveryIdempotencyAction.REQUEST")
        .contains("REQUEST_ACCEPTED", "SafePayload.from(result)", "result.outcome().name()")
        .contains("UsernameRecoveryRequestResult.Outcome.valueOf(outcome)")
        .doesNotContain(
            "AuthFlowSessionId",
            "authFlowSessionId",
            "rawCsrfToken",
            "String username",
            "String recipient",
            "MessageDeliveryCommand",
            "UsernameRecoveryIdempotencyPort");
  }

  @Test
  void productionSwitchIsAtomicAndCancelRemainsLegacy() throws Exception {
    String source = Files.readString(CONFIG);
    assertThat(source)
        .contains("return new IdempotentStartUsernameRecoveryService(")
        .contains("return new IdempotentRequestUsernameRecoveryService(")
        .doesNotContain("return new RequestUsernameRecoveryService(")
        .contains("return new IdempotentCancelUsernameRecoveryService(");
  }
}
