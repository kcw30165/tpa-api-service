package com.bct.ngtpa.apiservice.application.usecase.auth.usernamerecovery;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Fail-first contract for Username Recovery START integration over the shared executor. */
class IdempotentStartUsernameRecoveryServiceFailFirstTest {
  private static final Path COORDINATOR =
      Path.of(
          "src/main/java/com/bct/ngtpa/apiservice/application/usecase/auth/usernamerecovery/IdempotentStartUsernameRecoveryService.java");
  private static final Path CONFIG =
      Path.of("src/main/java/com/bct/ngtpa/apiservice/config/UsernameRecoveryFeatureConfig.java");

  @Test
  void requiresParallelSharedCoordinator() {
    assertThat(COORDINATOR).exists();
  }

  @Test
  void coordinatorValidatesBeforeSharedReservationAndUsesExactStartPolicy() throws Exception {
    if (!Files.exists(COORDINATOR)) return;
    String source = Files.readString(COORDINATOR);
    String compact = source.replaceAll("\\s+", "");
    assertThat(source)
        .contains(
            "implements StartUsernameRecoveryUseCase",
            "UsernameRecoveryStartValidator",
            "IdempotencyExecutor",
            "IdempotentExecutionCommand",
            "UsernameRecoveryIdempotencyAction.START",
            "UsernameRecoveryIdempotencyIntegrationPolicy",
            "UsernameRecoveryStartProtectedOperation",
            "UsernameRecoveryStartExecutionBridge",
            "policy.possibleSideEffect()",
            "responses::hydrate")
        .doesNotContain(
            "UsernameRecoveryIdempotencyPort",
            "UsernameRecoveryRequestHashGenerator",
            "adapter.out.redis",
            "adapter.in.web",
            "reserve(",
            "startProcessing(",
            "markCompleted(",
            "markFailed(",
            "recordUnknownOutcome(");
    assertThat(compact.indexOf("validator.validate(command)"))
        .isLessThan(compact.indexOf("executor.execute("));
  }

  @Test
  void acquiredExecutionUsesProtectedOperationAndProtectedReplayCapture() throws Exception {
    if (!Files.exists(COORDINATOR)) return;
    String source = Files.readString(COORDINATOR);
    assertThat(source)
        .containsIgnoringWhitespaces("operation.execute(command, validation)")
        .contains("responses.toTransientResponse(")
        .contains("response -> responses.capture(response, ttl)")
        .contains("case ACQUIRED, COMPLETED_REPLAY")
        .contains("responses.fromTransientResponse(response)");
  }

  @Test
  void lifecycleOutcomesPreserveEstablishedTypedExceptions() throws Exception {
    if (!Files.exists(COORDINATOR)) return;
    String source = Files.readString(COORDINATOR);
    assertThat(source)
        .contains(
            "case DUPLICATE_IN_PROGRESS",
            "UsernameRecoveryStartException.idempotencyInProgress()",
            "case KEY_REUSED_WITH_DIFFERENT_REQUEST",
            "UsernameRecoveryStartException.keyReused()",
            "case FAILED_EXISTING",
            "UsernameRecoveryStartException.systemFailure()",
            "case UNKNOWN_OUTCOME_EXISTING",
            "UsernameRecoveryStartException.unknownOutcomeExisting()");
  }

  @Test
  void productionUsesSharedCoordinatorAfterAtomicSwitch() throws Exception {
    String config = Files.readString(CONFIG);
    assertThat(config)
        .contains("new IdempotentStartUsernameRecoveryService(")
        .doesNotContain("new StartUsernameRecoveryService(");
  }
}
