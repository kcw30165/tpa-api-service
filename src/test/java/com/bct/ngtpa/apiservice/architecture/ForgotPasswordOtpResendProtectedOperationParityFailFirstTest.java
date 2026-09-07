package com.bct.ngtpa.apiservice.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ForgotPasswordOtpResendProtectedOperationParityFailFirstTest {
  private static final Path OPERATION =
      Path.of(
          "src/main/java/com/bct/ngtpa/apiservice/application/usecase/auth/forgotpassword/otp/resend/ForgotPasswordOtpResendProtectedOperation.java");
  private static final Path RESULT =
      Path.of(
          "src/main/java/com/bct/ngtpa/apiservice/application/usecase/auth/forgotpassword/otp/resend/ForgotPasswordOtpResendProtectedExecutionResult.java");

  @Test
  void protectedOperationOwnsLockScopedResendMutation() throws Exception {
    assertThat(OPERATION).exists();
    String source = Files.readString(OPERATION);
    assertThat(source)
        .contains(
            "Mono.usingWhen(",
            "loadSnapshot(",
            "validateEligibility(",
            "createCandidateAndDeliver(",
            "stagePending(",
            "mutateSession(")
        .doesNotContain(
            "ResendForgotPasswordOtpService",
            "ForgotPasswordIdempotencyPort",
            "ForgotPasswordIdempotencyRecord",
            "ForgotPasswordOtpCommitCoordinator");
  }

  @Test
  void acceptedDeliveryPersistsProtectedCommitBeforeReturningReference() throws Exception {
    assertThat(OPERATION).exists();
    String source = Files.readString(OPERATION);
    assertThat(source)
        .contains(
            "saveIfAbsent(",
            "ForgotPasswordOtpCommitState(",
            "new ProtectedReplayReference(",
            "ResendForgotPasswordOtpStatus.RESENT")
        .doesNotContain("markCompleted(", "attachPendingCommitRef(");
  }

  @Test
  void deliveryFailureAndUnknownOutcomeRemainDistinct() throws Exception {
    assertThat(OPERATION).exists();
    String source = Files.readString(OPERATION);
    assertThat(source)
        .contains(
            "MessageDeliveryStatus.FAILED",
            "ResendForgotPasswordOtpStatus.DELIVERY_FAILED",
            "ForgotPasswordProtectedPhase.OTP_DELIVERY_UNKNOWN",
            "Mono.error(")
        .doesNotContain("markFailed(", "markUnknownOutcome(");
  }

  @Test
  void protectedExecutionResultRequiresReferenceOnlyForResent() throws Exception {
    assertThat(RESULT).exists();
    String source = Files.readString(RESULT);
    assertThat(source)
        .contains(
            "ResendForgotPasswordOtpResult result",
            "ProtectedReplayReference replayReference",
            "ResendForgotPasswordOtpStatus.RESENT")
        .contains("IllegalArgumentException");
  }

  @Test
  void productionCompositionUsesSharedCoordinator() throws Exception {
    String config =
        Files.readString(
            Path.of(
                "src/main/java/com/bct/ngtpa/apiservice/config/auth/forgotpassword/ForgotPasswordApplicationConfig.java"));
    assertThat(config)
        .contains(".IdempotentResendForgotPasswordOtpService(")
        .doesNotContain("new ResendForgotPasswordOtpService(");
  }
}
