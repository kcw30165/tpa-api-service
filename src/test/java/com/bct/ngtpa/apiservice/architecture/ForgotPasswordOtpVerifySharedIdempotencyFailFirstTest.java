package com.bct.ngtpa.apiservice.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Fail-first boundary for the P5-C OTP_VERIFY shared-idempotency migration. */
class ForgotPasswordOtpVerifySharedIdempotencyFailFirstTest {
  private static final Path VERIFY =
      Path.of(
          "src/main/java/com/bct/ngtpa/apiservice/application/usecase/auth/forgotpassword/otp/verify");
  private static final Path INTEGRATION =
      Path.of(
          "src/main/java/com/bct/ngtpa/apiservice/application/usecase/auth/forgotpassword/idempotency/otpverify");
  private static final Path CONFIG =
      Path.of(
          "src/main/java/com/bct/ngtpa/apiservice/config/auth/forgotpassword/ForgotPasswordApplicationConfig.java");

  @Test
  void sharedCoordinatorAndProtectedOperationMustExist() {
    assertThat(VERIFY.resolve("IdempotentVerifyForgotPasswordOtpService.java")).exists();
    assertThat(VERIFY.resolve("ForgotPasswordOtpVerifyProtectedOperation.java")).exists();
    assertThat(VERIFY.resolve("ForgotPasswordOtpVerifyProtectedExecutionResult.java")).exists();
  }

  @Test
  void typedResponseBridgeAndStrictReplayCodecMustExist() {
    assertThat(INTEGRATION.resolve("ForgotPasswordOtpVerifyExecutionBridge.java")).exists();
    assertThat(INTEGRATION.resolve("ForgotPasswordOtpVerifyReplayCodec.java")).exists();
  }

  @Test
  void coordinatorMustUseSharedExecutorAndCanonicalConstantFingerprint() throws Exception {
    Path coordinator = VERIFY.resolve("IdempotentVerifyForgotPasswordOtpService.java");
    assertThat(coordinator).exists();
    String source = Files.readString(coordinator);
    assertThat(source)
        .contains(
            "IdempotencyExecutor",
            "ForgotPasswordIdempotencyAction.OTP_VERIFY",
            "policy.otpVerify()",
            "IdempotencyFailureClassifiers.possibleSideEffect()")
        .doesNotContain(
            "ForgotPasswordIdempotencyPort",
            "startProcessing(",
            "markCompleted(",
            "markFailed(",
            "markUnknownOutcome(",
            "adapter.out.redis",
            "org.springframework");
  }

  @Test
  void replayCodecMustPreserveOnlyPublicResultAndRejectSensitiveVerifyInputs() throws Exception {
    Path codec = INTEGRATION.resolve("ForgotPasswordOtpVerifyReplayCodec.java");
    assertThat(codec).exists();
    String source = Files.readString(codec);
    assertThat(source)
        .contains("OTP_VERIFY", "FAIL_ON_UNKNOWN_PROPERTIES", "remainingVerifyAttempts");
    assertThat(containsJavaIdentifier(source, "csrfToken")).isFalse();
    assertThat(containsJavaIdentifier(source, "code")).isFalse();
    assertThat(containsJavaIdentifier(source, "idempotencyKey")).isFalse();
    assertThat(containsJavaIdentifier(source, "requestId")).isFalse();
    assertThat(containsJavaIdentifier(source, "origin")).isFalse();
    assertThat(containsJavaIdentifier(source, "referer")).isFalse();
    assertThat(containsJavaIdentifier(source, "activeOtpChallengeId")).isFalse();
    assertThat(containsJavaIdentifier(source, "resetSubjectId")).isFalse();
    assertThat(containsJavaIdentifier(source, "authorizationId")).isFalse();
    assertThat(containsJavaIdentifier(source, "protectedDestination")).isFalse();
  }

  private static boolean containsJavaIdentifier(String source, String identifier) {
    return java.util.regex.Pattern.compile(
            "(?<![A-Za-z0-9_$])" + java.util.regex.Pattern.quote(identifier) + "(?![A-Za-z0-9_$])")
        .matcher(source)
        .find();
  }

  @Test
  void productionCompositionMustUseSharedCoordinatorAfterParityIsProven() throws Exception {
    String config = Files.readString(CONFIG);
    assertThat(config).contains(".IdempotentVerifyForgotPasswordOtpService(");
  }
}
