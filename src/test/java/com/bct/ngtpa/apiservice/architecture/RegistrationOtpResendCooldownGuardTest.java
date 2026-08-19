package com.bct.ngtpa.apiservice.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class RegistrationOtpResendCooldownGuardTest {

  @Test
  void registrationOtpChallengeAdapterOverridesResendOtpAndUsesReplaceChallenge() throws Exception {
    Path sourcePath =
        Path.of(
            "src/main/java/com/bct/ngtpa/apiservice/adapter/out/registration/RegistrationOtpChallengeAdapter.java");
    assertThat(Files.exists(sourcePath)).isTrue();
    String source = Files.readString(sourcePath);
    assertThat(source)
        .containsIgnoringWhitespaces(
            "public Mono<RegistrationOtpCreationResult> resendOtp",
            "otpChallengePort.replaceChallenge",
            "new ReplaceOtpChallengeCommand",
            "COOLDOWN_ACTIVE",
            "MAX_RESEND_EXCEEDED");
  }

  @Test
  void resendServiceMapsExpectedCooldownRejectionInsteadOfSystemFailure() throws Exception {
    Path sourcePath =
        Path.of(
            "src/main/java/com/bct/ngtpa/apiservice/application/usecase/auth/registration/otp/ResendRegistrationOtpService.java");
    assertThat(Files.exists(sourcePath)).isTrue();
    String source = Files.readString(sourcePath);
    assertThat(source)
        .containsIgnoringWhitespaces(
            "onErrorResume(RegistrationOtpResendRejectedException.class",
            "REGISTRATION_OTP_COOLDOWN_ACTIVE",
            "REGISTRATION_OTP_MAX_ATTEMPTS_REACHED");
  }
}
