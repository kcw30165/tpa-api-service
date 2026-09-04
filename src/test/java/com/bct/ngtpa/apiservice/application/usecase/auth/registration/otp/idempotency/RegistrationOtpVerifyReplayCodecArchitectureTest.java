package com.bct.ngtpa.apiservice.application.usecase.auth.registration.otp.idempotency;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class RegistrationOtpVerifyReplayCodecArchitectureTest {
  @Test
  void safeReplayCodecExcludesProtectedVerifyInputsAndProtectedReplayStore() throws Exception {
    String source =
        Files.readString(
            Path.of(
                "src/main/java/com/bct/ngtpa/apiservice/application/usecase/auth/registration/otp/idempotency/RegistrationOtpVerifyReplayCodec.java"));
    assertThat(source)
        .contains("OTP_VERIFY", "accountEnv", "trustCode", "FAIL_ON_UNKNOWN_PROPERTIES");
    assertThat(source)
        .doesNotContain(
            "authFlowSessionId",
            "csrfToken",
            "otpCode",
            "activeOtpChallengeId",
            "identityValue",
            "dateOfBirth",
            "contactValue",
            "email",
            "mobileNumber",
            "policyNo",
            "certNo",
            "acceptLanguage",
            "correlationId",
            "ProtectedReplayPort",
            "protectedReplayReference");
  }
}
