package com.bct.ngtpa.apiservice.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Fail-first extraction contract for the idempotency-neutral COMPLETE protected operation. */
class ForgotPasswordCompleteProtectedOperationParityFailFirstTest {
  private static final Path OPERATION =
      Path.of(
          "src/main/java/com/bct/ngtpa/apiservice/application/usecase/auth/forgotpassword/complete/ForgotPasswordCompleteProtectedOperation.java");

  @Test
  void requiresProtectedOperationImplementation() {
    assertThat(OPERATION).exists();
  }

  @Test
  void operationOwnsLockValidationDispatchClassificationAndTerminalSessionMutation()
      throws Exception {
    if (!Files.exists(OPERATION)) return;
    String source = Files.readString(OPERATION);
    assertThat(source)
        .contains(
            "AuthFlowSessionMutationLockPort",
            "ForgotPasswordCompletionPort",
            "ForgotPasswordResetAuthorizationValidator",
            "ForgotPasswordPasswordValidator",
            "RevisionAwareAuthFlowSessionWriter",
            "AuthFlowStatus.COMPLETING",
            "AuthFlowStatus.COMPLETED",
            "AuthFlowStatus.UNKNOWN_OUTCOME_BLOCKED",
            "ForgotPasswordCompletionResult.Status.RESET",
            "ForgotPasswordCompletionResult.Status.REJECTED",
            "ForgotPasswordCompleteProtectedExecutionResult.terminal",
            "ForgotPasswordCompleteProtectedExecutionResult.technicalFailure",
            "ForgotPasswordCompleteProtectedExecutionResult.unknownOutcome");
  }

  @Test
  void operationContainsNoIdempotencyLifecycleOrBlockingSubscription() throws Exception {
    if (!Files.exists(OPERATION)) return;
    assertThat(Files.readString(OPERATION))
        .doesNotContain(
            "IdempotencyExecutor",
            "ForgotPasswordIdempotencyPort",
            "ForgotPasswordIdempotencyAction",
            "markCompleted(",
            "markFailed(",
            "markUnknownOutcome(",
            "requestHash(",
            ".block(",
            ".subscribe(");
  }

  @Test
  void operationDoesNotPersistOrLogPasswordAndProtectedContext() throws Exception {
    if (!Files.exists(OPERATION)) return;
    assertThat(Files.readString(OPERATION))
        .doesNotContain(
            "System.out",
            "logger.info",
            "logger.debug",
            "command.toString()",
            "context.toString()");
  }
}
