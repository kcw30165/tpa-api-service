package com.bct.ngtpa.apiservice.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Executable preservation guards for the audited COMPLETE protected-operation behavior matrix. */
class ForgotPasswordCompleteProtectedOperationBehaviorGuardTest {
  private static final Path OPERATION =
      Path.of(
          "src/main/java/com/bct/ngtpa/apiservice/application/usecase/auth/forgotpassword/complete/ForgotPasswordCompleteProtectedOperation.java");

  @Test
  void successConsumesAuthorizationAndCompletesTheSession() throws Exception {
    String source = Files.readString(OPERATION);
    assertThat(source)
        .contains(
            "ForgotPasswordCompletionResult.Status.RESET",
            "authorization.expiresAt()",
            "ForgotPasswordResetAuthorization consumed",
            "withResetAuthorization(consumed)",
            "AuthFlowStatus.COMPLETING",
            "AuthFlowStatus.COMPLETED",
            "WorkflowNextStep.RETURN_TO_LOGIN");
  }

  @Test
  void recognizedBusinessRejectionsPreserveDistinctTerminalBehavior() throws Exception {
    String source = Files.readString(OPERATION);
    assertThat(source)
        .contains(
            "ForgotPasswordCompleteErrorKeys.PASSWORD_INVALID",
            "passwordInvalid()",
            "ForgotPasswordCompleteErrorKeys.MEMBER_NOT_FOUND",
            "memberNotFound()",
            "WorkflowNextStep.PASSWORD_SETUP",
            "WorkflowNextStep.RETURN_TO_LOGIN",
            "CompleteForgotPasswordStatus.BUSINESS_REJECTED");
  }

  @Test
  void uncertainDispatchAndTerminalMutationBlockTheSession() throws Exception {
    String source = Files.readString(OPERATION);
    assertThat(source)
        .contains(
            "ForgotPasswordCompletionResult.Status.UNKNOWN_OUTCOME",
            "onErrorResume(ignored -> blockUnknown(session, now))",
            "AuthFlowStatus.UNKNOWN_OUTCOME_BLOCKED",
            "ForgotPasswordCompleteProtectedExecutionResult.unknownOutcome");
  }

  @Test
  void lockAndTrustedActorRoleStayInsideTheProtectedBoundary() throws Exception {
    String source = Files.readString(OPERATION);
    assertThat(source)
        .contains(
            "return locks",
            ".acquire(input.command().authFlowSessionId(), lockLease)",
            "Mono.usingWhen(",
            "command.actorUserRole()",
            "releaseAfterSuccess",
            "releaseAfterFailure",
            "releaseAfterCancellation");
  }

  @Test
  void protectedBoundaryOwnsNoIdempotencyLifecycleOrSensitiveLogging() throws Exception {
    assertThat(Files.readString(OPERATION))
        .doesNotContain(
            "IdempotencyExecutor",
            "ForgotPasswordIdempotencyPort",
            "markCompleted(",
            "markFailed(",
            "requestHash(",
            ".block(",
            ".subscribe(",
            "System.out",
            "logger.info",
            "logger.debug");
  }
}
