package com.bct.ngtpa.apiservice.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class RegistrationOtpChallengeAdapterVerifyGuardTest {

  @Test
  void registrationOtpChallengeAdapterOverridesVerifyOtpAndDelegatesToOtpChallengePort()
      throws Exception {
    Path sourcePath =
        Path.of(
            "src/main/java/com/bct/ngtpa/apiservice/adapter/out/registration/RegistrationOtpChallengeAdapter.java");
    assertThat(Files.exists(sourcePath)).isTrue();
    String source = Files.readString(sourcePath);
    assertThat(source)
        .containsIgnoringWhitespaces(
            "public Mono<RegistrationOtpVerificationResult> verifyOtp",
            "otpChallengePort.verifyChallenge",
            "new VerifyOtpChallengeCommand",
            "RegistrationOtpVerificationResult.verified()",
            "RegistrationOtpVerificationResult.invalid(result.remainingAttempts())");
  }
}
