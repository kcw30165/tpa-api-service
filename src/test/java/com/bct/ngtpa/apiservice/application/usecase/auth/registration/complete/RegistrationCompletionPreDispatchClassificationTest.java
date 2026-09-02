package com.bct.ngtpa.apiservice.application.usecase.auth.registration.complete;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class RegistrationCompletionPreDispatchClassificationTest {
  @Test
  void exceptionIsApplicationOwnedAndDoesNotExposeInfrastructureDetails() {
    var failure =
        new RegistrationCompletionPreDispatchException(new IllegalStateException("internal"));
    assertThat(failure.getMessage())
        .isEqualTo("Registration completion failed before downstream dispatch");
    assertThat(failure.getMessage()).doesNotContain("APIM", "profile", "accountEnv", "URL");
  }

  @Test
  void knownPreDispatchBranchPrecedesUnknownOutcomeBranch() throws Exception {
    String source =
        Files.readString(
            Path.of(
                "src/main/java/com/bct/ngtpa/apiservice/application/usecase/auth/registration/complete/CompleteRegistrationService.java"));
    int known = source.indexOf("RegistrationCompletionPreDispatchException.class");
    int unknown = source.indexOf(".markUnknownOutcome(", known);
    assertThat(known).isGreaterThanOrEqualTo(0);
    assertThat(unknown).isGreaterThan(known);
    assertThat(source.substring(known, unknown))
        .contains(
            ".markFailed(",
            "CompleteRegistrationResultStatus.SYSTEM_FAILED",
            "SharedWorkflowErrorKey.SYSTEM_UNAVAILABLE");
  }
}
