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
        .isEqualTo("Registration completion failed before downstream dispatch")
        .doesNotContain("APIM", "profile", "accountEnv", "URL");
  }

  @Test
  void sharedCoordinatorClassifiesKnownPreDispatchSeparatelyFromAmbiguousFailure()
      throws Exception {
    String source =
        Files.readString(
            Path.of(
                "src/main/java/com/bct/ngtpa/apiservice/application/usecase/auth/registration/complete/"
                    + "idempotency/SharedCompleteRegistrationService.java"));
    assertThat(source)
        .contains("failure instanceof RegistrationCompletionPreDispatchException")
        .contains("IdempotencyFailureClassification.PROVEN_NO_SIDE_EFFECT")
        .contains("IdempotencyFailureClassification.POSSIBLE_SIDE_EFFECT")
        .doesNotContain(".markFailed(", ".markUnknownOutcome(");
  }
}
