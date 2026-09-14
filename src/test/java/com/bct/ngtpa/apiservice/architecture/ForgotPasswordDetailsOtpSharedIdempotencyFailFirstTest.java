package com.bct.ngtpa.apiservice.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Fail-first boundary for the P5-C DETAILS_AND_OTP shared-idempotency migration. */
class ForgotPasswordDetailsOtpSharedIdempotencyFailFirstTest {
  private static final Path DETAILS_OTP =
      Path.of(
          "src/main/java/com/bct/ngtpa/apiservice/application/usecase/auth/forgotpassword/detailsotp");
  private static final Path CONFIG =
      Path.of(
          "src/main/java/com/bct/ngtpa/apiservice/config/auth/forgotpassword/ForgotPasswordApplicationConfig.java");

  @Test
  void sharedCoordinatorAndProtectedOperationMustExist() throws Exception {
    assertThat(DETAILS_OTP.resolve("IdempotentForgotPasswordDetailsOtpService.java")).exists();
    assertThat(DETAILS_OTP.resolve("ForgotPasswordDetailsOtpProtectedOperation.java")).exists();
  }

  @Test
  void responseBridgeAndProtectedReplayCodecMustExist() throws Exception {
    Path integration =
        Path.of(
            "src/main/java/com/bct/ngtpa/apiservice/application/usecase/auth/forgotpassword/idempotency/detailsotp");
    assertThat(integration.resolve("ForgotPasswordDetailsOtpExecutionBridge.java")).exists();
    assertThat(integration.resolve("ForgotPasswordDetailsOtpReplayCodec.java")).exists();
    assertThat(integration.resolve("ForgotPasswordDetailsOtpProtectedReplayPort.java")).exists();
  }

  @Test
  void productionCompositionMustOwnSharedExecutorPath() throws Exception {
    String config = Files.readString(CONFIG);
    assertThat(config)
        .contains("new IdempotentForgotPasswordDetailsOtpService(")
        .contains("IdempotencyExecutor")
        .doesNotContain("new ForgotPasswordDetailsOtpOrchestrationService(");
  }

  @Test
  void migratedActionMustNotUseLegacyLifecyclePort() throws Exception {
    String sources =
        Files.walk(DETAILS_OTP)
            .filter(path -> path.toString().endsWith(".java"))
            .map(ForgotPasswordDetailsOtpSharedIdempotencyFailFirstTest::read)
            .reduce("", (left, right) -> left + "\n" + right);
    assertThat(sources)
        .doesNotContain("ForgotPasswordIdempotencyPort")
        .doesNotContain("startProcessing(")
        .doesNotContain("attachPendingCommitRef(")
        .doesNotContain("markCompleted(")
        .doesNotContain("markFailed(")
        .doesNotContain("markUnknownOutcome(");
  }

  @Test
  void remainingLegacyActionsMustStayComposed() throws Exception {
    String config = Files.readString(CONFIG);
    assertThat(config)
        .contains("IdempotentResendForgotPasswordOtpService")
        .contains("IdempotentVerifyForgotPasswordOtpService")
        .contains("IdempotentCompleteForgotPasswordService")
        .contains("CancelForgotPasswordService");
  }

  private static String read(Path path) {
    try {
      return Files.readString(path);
    } catch (Exception exception) {
      throw new IllegalStateException(exception);
    }
  }
}
