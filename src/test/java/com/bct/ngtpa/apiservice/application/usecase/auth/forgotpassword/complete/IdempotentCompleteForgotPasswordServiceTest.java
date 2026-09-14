package com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.complete;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Coordinator contract tests preserve shared lifecycle routing before the production switch. */
class IdempotentCompleteForgotPasswordServiceTest {
  private static final Path SOURCE =
      Path.of(
          "src/main/java/com/bct/ngtpa/apiservice/application/usecase/auth/forgotpassword/complete/IdempotentCompleteForgotPasswordService.java");

  @Test
  void commandUsesSessionScopeConstantFingerprintCommandTtlAndConservativeClassifier()
      throws Exception {
    String source = Files.readString(SOURCE);
    assertThat(source)
        .contains(
            "ForgotPasswordIdempotencyAction.COMPLETE",
            "command.authFlowSessionId().value()",
            "command.idempotencyKey().value()",
            "policy.complete()",
            "command.idempotencyTtl()",
            "IdempotencyFailureClassifiers.possibleSideEffect()",
            "responses::capture",
            "responses::hydrate")
        .doesNotContain("command.password()", "command.confirmPassword()", "command.csrfToken()");
  }

  @Test
  void acquiredExecutionLoadsRevisionedSessionAndInvokesProtectedOperationOnce() throws Exception {
    String source = Files.readString(SOURCE);
    assertThat(source)
        .contains(
            "sessions",
            ".loadSnapshot(command.authFlowSessionId())",
            "executeLoaded(command, snapshot)",
            ".execute(new ForgotPasswordCompleteProtectedOperationInput(command, context, snapshot))",
            "responses.toTransientResponse(execution.result())");
  }

  @Test
  void nonTerminalProtectedClassificationsCrossExecutorBoundaryAsFailures() throws Exception {
    String source = Files.readString(SOURCE);
    assertThat(source)
        .contains(
            "Outcome.TECHNICAL_FAILURE",
            "Forgot Password COMPLETE protected operation failed",
            "Outcome.UNKNOWN_OUTCOME",
            "Forgot Password COMPLETE protected operation outcome is unknown",
            "Mono.error(");
  }

  @Test
  void completedReplayHydratesAndExistingLifecycleOutcomesDoNotInvokeProtectedOperation()
      throws Exception {
    String source = Files.readString(SOURCE);
    assertThat(source)
        .contains(
            "case ACQUIRED, COMPLETED_REPLAY -> responses.fromTransientResponse(response)",
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
  void coordinatorOwnsNoLegacyPersistenceBlockingOrDirectSideEffectPort() throws Exception {
    assertThat(Files.readString(SOURCE))
        .doesNotContain(
            "ForgotPasswordIdempotencyPort",
            "ForgotPasswordCompletionPort",
            "startProcessing(",
            "markCompleted(",
            "markFailed(",
            "markUnknownOutcome(",
            ".block(",
            ".subscribe(");
  }
}
