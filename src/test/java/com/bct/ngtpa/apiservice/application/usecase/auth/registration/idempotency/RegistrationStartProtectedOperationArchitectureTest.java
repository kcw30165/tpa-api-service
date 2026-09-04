package com.bct.ngtpa.apiservice.application.usecase.auth.registration.idempotency;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class RegistrationStartProtectedOperationArchitectureTest {
  @Test
  void protectedOperationHasNoLegacyOrSharedPersistenceDependency() throws Exception {
    String source =
        Files.readString(
            Path.of(
                "src/main/java/com/bct/ngtpa/apiservice/application/usecase/auth/registration/idempotency/RegistrationStartProtectedOperation.java"));
    assertThat(source)
        .doesNotContain(
            "RegistrationIdempotencyPort",
            "IdempotencyExecutor",
            "markCompleted(",
            "markFailed(",
            "startProcessing(",
            ".adapter.out.",
            "org.springframework.");
  }
}
