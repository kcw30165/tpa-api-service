package com.bct.ngtpa.apiservice.application.usecase.auth.registration.otp.idempotency;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class RegistrationOtpVerifyProtectedOperationArchitectureTest {
  @Test
  void protectedOperationHasNoIdempotencyOrAdapterDependency() throws Exception {
    String source =
        Files.readString(
            Path.of(
                "src/main/java/com/bct/ngtpa/apiservice/application/usecase/auth/registration/otp/idempotency/RegistrationOtpVerifyProtectedOperation.java"));
    assertThat(source)
        .doesNotContain(
            "RegistrationIdempotencyPort",
            "IdempotencyExecutor",
            "RegistrationRequestHashGenerator",
            "RegistrationSharedIdempotencyIdentityFactory",
            "RegistrationSharedFingerprintFactory",
            "startProcessing(",
            "markCompleted(",
            "markFailed(",
            "adapter.out.redis",
            "adapter.in.web",
            "org.springframework");
  }
}
