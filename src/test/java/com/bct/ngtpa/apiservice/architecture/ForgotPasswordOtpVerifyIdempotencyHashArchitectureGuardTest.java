package com.bct.ngtpa.apiservice.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ForgotPasswordOtpVerifyIdempotencyHashArchitectureGuardTest {
  private static final Path COORDINATOR =
      Path.of(
          "src/main/java/com/bct/ngtpa/apiservice/application/usecase/auth/forgotpassword/otp/verify/IdempotentVerifyForgotPasswordOtpService.java");
  private static final Path POLICY =
      Path.of(
          "src/main/java/com/bct/ngtpa/apiservice/application/usecase/auth/forgotpassword/idempotency/ForgotPasswordIdempotencyIntegrationPolicy.java");

  @Test
  void forgotPasswordOtpVerifyUsesCanonicalConstantFingerprintWithoutSensitiveInputs()
      throws Exception {
    String coordinator = Files.readString(COORDINATOR);
    String policy = Files.readString(POLICY);

    assertThat(coordinator)
        .contains("ForgotPasswordIdempotencyAction.OTP_VERIFY", "policy.otpVerify()")
        .doesNotContain("command.code()", "command.csrfToken()", "command.requestId()");
    assertThat(policy)
        .contains(
            "public IdempotencyFingerprint otpVerify()",
            "ForgotPasswordIdempotencyAction.OTP_VERIFY",
            "List.of(")
        .doesNotContain("sessionId", "csrfToken");
  }
}
