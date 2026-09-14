package com.bct.ngtpa.apiservice.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Fail-first boundary for the P5-C Forgot Password COMPLETE shared-idempotency migration. */
class ForgotPasswordCompleteSharedIdempotencyFailFirstTest {
  private static final Path COMPLETE =
      Path.of(
          "src/main/java/com/bct/ngtpa/apiservice/application/usecase/auth/forgotpassword/complete");
  private static final Path IDEMPOTENCY =
      Path.of(
          "src/main/java/com/bct/ngtpa/apiservice/application/usecase/auth/forgotpassword/idempotency/complete");
  private static final Path POLICY =
      Path.of(
          "src/main/java/com/bct/ngtpa/apiservice/application/usecase/auth/forgotpassword/idempotency/ForgotPasswordIdempotencyIntegrationPolicy.java");

  @Test
  void requiresThinCoordinatorProtectedOperationAndTypedReplayBoundary() {
    assertThat(COMPLETE.resolve("IdempotentCompleteForgotPasswordService.java")).exists();
    assertThat(COMPLETE.resolve("ForgotPasswordCompleteProtectedOperation.java")).exists();
    assertThat(COMPLETE.resolve("ForgotPasswordCompleteProtectedExecutionResult.java")).exists();
    assertThat(IDEMPOTENCY.resolve("ForgotPasswordCompleteExecutionBridge.java")).exists();
    assertThat(IDEMPOTENCY.resolve("ForgotPasswordCompleteReplayCodec.java")).exists();
  }

  @Test
  void coordinatorMustUseSharedExecutorAndConstantPasswordFreeFingerprint() throws Exception {
    Path coordinator = COMPLETE.resolve("IdempotentCompleteForgotPasswordService.java");
    if (!Files.exists(coordinator)) return;
    String source = Files.readString(coordinator);
    assertThat(source)
        .contains(
            "IdempotencyExecutor", "ForgotPasswordIdempotencyAction.COMPLETE", "policy.complete()")
        .doesNotContain(
            "ForgotPasswordIdempotencyPort",
            "password()",
            "confirmPassword()",
            "block(",
            "subscribe(");
    assertThat(Files.readString(POLICY))
        .contains(
            "public IdempotencyFingerprint complete()",
            "ForgotPasswordIdempotencyAction.COMPLETE",
            "List.of(",
            "v1");
  }

  @Test
  void protectedOperationMustOwnBusinessDispatchButNoIdempotencyLifecycle() throws Exception {
    Path operation = COMPLETE.resolve("ForgotPasswordCompleteProtectedOperation.java");
    if (!Files.exists(operation)) return;
    assertThat(Files.readString(operation))
        .contains("ForgotPasswordCompletionPort", "AuthFlowSessionPort")
        .doesNotContain(
            "IdempotencyExecutor",
            "ForgotPasswordIdempotencyPort",
            "markCompleted(",
            "markFailed(",
            "markUnknownOutcome(");
  }

  @Test
  void replayBoundaryMustBeStrictAndMustNotPersistPasswordOrSessionSecrets() throws Exception {
    Path codec = IDEMPOTENCY.resolve("ForgotPasswordCompleteReplayCodec.java");
    if (!Files.exists(codec)) return;
    assertThat(Files.readString(codec))
        .contains("COMPLETE", "FAIL_ON_UNKNOWN_PROPERTIES")
        .doesNotContain(
            "command.password()",
            "command.confirmPassword()",
            "command.csrfToken()",
            "command.authFlowSessionId()");
  }
}
