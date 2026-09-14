package com.bct.ngtpa.apiservice.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Fail-first contract for COMPLETE integration over the repository-wide shared executor. */
class ForgotPasswordCompleteSharedCoordinatorFailFirstTest {
  private static final Path COORDINATOR =
      Path.of(
          "src/main/java/com/bct/ngtpa/apiservice/application/usecase/auth/forgotpassword/complete/IdempotentCompleteForgotPasswordService.java");

  @Test
  void requiresSharedCoordinatorImplementation() {
    assertThat(COORDINATOR).exists();
  }

  @Test
  void coordinatorUsesConstantFingerprintSessionScopeAndPossibleSideEffectClassifier()
      throws Exception {
    if (!Files.exists(COORDINATOR)) return;
    assertThat(Files.readString(COORDINATOR))
        .contains(
            "implements CompleteForgotPasswordUseCase",
            "IdempotencyExecutor",
            "IdempotentExecutionCommand",
            "ForgotPasswordIdempotencyAction.COMPLETE",
            "command.authFlowSessionId().value()",
            "command.idempotencyKey().value()",
            "policy.complete()",
            "command.idempotencyTtl()",
            "IdempotencyFailureClassifiers.possibleSideEffect()",
            "responses::capture",
            "responses::hydrate");
  }

  @Test
  void acquiredExecutionRoutesClassifiedProtectedResultsBeforeCapture() throws Exception {
    if (!Files.exists(COORDINATOR)) return;
    assertThat(Files.readString(COORDINATOR))
        .contains(
            "return operation",
            ".execute(new ForgotPasswordCompleteProtectedOperationInput(",
            "ForgotPasswordCompleteProtectedExecutionResult.Outcome.TERMINAL",
            "ForgotPasswordCompleteProtectedExecutionResult.Outcome.TECHNICAL_FAILURE",
            "ForgotPasswordCompleteProtectedExecutionResult.Outcome.UNKNOWN_OUTCOME",
            "responses.toTransientResponse(",
            "Mono.error(");
  }

  @Test
  void lifecycleOutcomesPreserveExistingPublicStatusesWithoutProtectedExecution() throws Exception {
    if (!Files.exists(COORDINATOR)) return;
    assertThat(Files.readString(COORDINATOR))
        .contains(
            "case ACQUIRED, COMPLETED_REPLAY",
            "case DUPLICATE_IN_PROGRESS",
            "CompleteForgotPasswordStatus.DUPLICATE_IN_PROGRESS",
            "case KEY_REUSED_WITH_DIFFERENT_REQUEST",
            "CompleteForgotPasswordStatus.IDEMPOTENCY_KEY_REUSED",
            "case FAILED_EXISTING",
            "CompleteForgotPasswordStatus.SYSTEM_FAILED",
            "case UNKNOWN_OUTCOME_EXISTING",
            "CompleteForgotPasswordStatus.UNKNOWN_OUTCOME");
  }

  @Test
  void coordinatorOwnsNoLegacyIdempotencyPersistenceOrSensitiveFingerprintInput() throws Exception {
    if (!Files.exists(COORDINATOR)) return;
    assertThat(Files.readString(COORDINATOR))
        .doesNotContain(
            "ForgotPasswordIdempotencyPort",
            "startProcessing(",
            "markCompleted(",
            "markFailed(",
            "markUnknownOutcome(",
            "command.password()",
            "command.confirmPassword()",
            "command.csrfToken()",
            ".block(",
            ".subscribe(");
  }
}
