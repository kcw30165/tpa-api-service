package com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.otp.resend;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class IdempotentResendForgotPasswordOtpServiceTest {
  private static final Path SOURCE =
      Path.of(
          "src/main/java/com/bct/ngtpa/apiservice/application/usecase/auth/forgotpassword/otp/resend/IdempotentResendForgotPasswordOtpService.java");

  @Test
  void coordinatorUsesSharedExecutorAndSessionScopedOtpResendIdentity() throws Exception {
    assertThat(SOURCE).exists();
    String source = Files.readString(SOURCE);
    assertThat(source)
        .contains("implements ResendForgotPasswordOtpUseCase")
        .contains("IdempotencyExecutor")
        .contains("IdempotentExecutionCommand")
        .contains("ForgotPasswordIdempotencyAction.OTP_RESEND")
        .contains("command.sessionId().value()")
        .contains("command.idempotencyKey()")
        .contains("policy.otpResend()")
        .contains("IdempotencyFailureClassifiers.possibleSideEffect()")
        .doesNotContain(
            "ForgotPasswordIdempotencyPort",
            "startProcessing(",
            "attachPendingCommitRef(",
            "markCompleted(",
            "markFailed(",
            "markUnknownOutcome(");
  }

  @Test
  void acquiredExecutionCarriesOpaqueReferenceIntoCapture() throws Exception {
    assertThat(SOURCE).exists();
    String source = Files.readString(SOURCE);
    assertThat(source)
        .containsIgnoringWhitespaces("operation.execute(command)")
        .contains("execution.replayReference()")
        .contains("withProtectedReplayReference(")
        .contains("responses.capture(response, response.protectedReplayReference())")
        .contains("responses::hydrate");
  }

  @Test
  void lifecycleOutcomesPreservePublicRouting() throws Exception {
    assertThat(SOURCE).exists();
    String source = Files.readString(SOURCE);
    assertThat(source)
        .contains("case ACQUIRED, COMPLETED_REPLAY")
        .contains("case DUPLICATE_IN_PROGRESS")
        .contains("ResendForgotPasswordOtpStatus.SYSTEM_FAILED")
        .contains("case KEY_REUSED_WITH_DIFFERENT_REQUEST")
        .contains("ResendForgotPasswordOtpStatus.IDEMPOTENCY_KEY_REUSED")
        .contains("case FAILED_EXISTING")
        .contains("ResendForgotPasswordOtpStatus.PREVIOUS_FAILED")
        .contains("case UNKNOWN_OUTCOME_EXISTING")
        .contains("ResendForgotPasswordOtpStatus.UNKNOWN_OUTCOME");
  }

  @Test
  void protectedSystemFailureCrossesExecutorBoundaryAsFailure() throws Exception {
    assertThat(SOURCE).exists();
    String source = Files.readString(SOURCE);
    assertThat(source)
        .contains("ResendForgotPasswordOtpStatus.SYSTEM_FAILED")
        .contains("Mono.error(")
        .contains("Forgot Password OTP_RESEND protected operation failed");
  }

  @Test
  void productionCompositionUsesValidatedSharedCoordinator() throws Exception {
    String config =
        Files.readString(
            Path.of(
                "src/main/java/com/bct/ngtpa/apiservice/config/auth/forgotpassword/ForgotPasswordApplicationConfig.java"));
    assertThat(config)
        .contains(".IdempotentResendForgotPasswordOtpService(")
        .doesNotContain("new ResendForgotPasswordOtpService(");
  }
}
