package com.bct.ngtpa.apiservice.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ForgotPasswordOtpVerifyProtectedOperationParityFailFirstTest {
  private static final Path OP =
      Path.of(
          "src/main/java/com/bct/ngtpa/apiservice/application/usecase/auth/forgotpassword/otp/verify/ForgotPasswordOtpVerifyProtectedOperation.java");

  @Test
  void ownsProtectedMutationWithoutLifecycle() throws Exception {
    String source = Files.readString(OP);
    assertThat(source)
        .contains(
            "Mono.usingWhen(",
            "loadSnapshot(",
            "verifyChallenge(",
            "RESET_AUTHORIZED",
            "RevisionAwareAuthFlowSessionWriter")
        .doesNotContain(
            "ForgotPasswordIdempotencyPort",
            "startProcessing(",
            "markCompleted(",
            "markFailed(",
            "markUnknownOutcome(");
  }

  @Test
  void preservesOutcomesAndServerSideAuthorization() throws Exception {
    String source = Files.readString(OP);
    assertThat(source)
        .contains(
            "OTP_INVALID",
            "OTP_EXPIRED",
            "OTP_LOCKED",
            "PASSWORD_SETUP",
            "RESEND_OTP",
            "RESTART_FLOW",
            "ForgotPasswordResetAuthorization",
            "withResetAuthorization");
  }

  @Test
  void productionCompositionUsesSharedCoordinator() throws Exception {
    String config =
        Files.readString(
            Path.of(
                "src/main/java/com/bct/ngtpa/apiservice/config/auth/forgotpassword/ForgotPasswordApplicationConfig.java"));
    assertThat(config).contains(".IdempotentVerifyForgotPasswordOtpService(");
  }
}
