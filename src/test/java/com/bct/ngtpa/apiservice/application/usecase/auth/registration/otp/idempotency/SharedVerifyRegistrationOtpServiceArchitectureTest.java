package com.bct.ngtpa.apiservice.application.usecase.auth.registration.otp.idempotency;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class SharedVerifyRegistrationOtpServiceArchitectureTest {
  @Test
  void coordinatorUsesSharedExecutorAndNeverPersistsRawOtp() throws Exception {
    String source =
        Files.readString(
            Path.of(
                "src/main/java/com/bct/ngtpa/apiservice/application/usecase/auth/registration/otp/idempotency/SharedVerifyRegistrationOtpService.java"));
    assertThat(source)
        .contains(
            "IdempotencyExecutor",
            "OTP_VERIFY",
            "sha256(command.otpCode())",
            "RegistrationSharedFailureClassifiers.possibleSideEffect()");
    assertThat(source)
        .doesNotContain(
            "RegistrationIdempotencyPort",
            "startProcessing(",
            "markCompleted(",
            "markFailed(",
            "adapter.out.redis",
            "org.springframework");
  }
}
