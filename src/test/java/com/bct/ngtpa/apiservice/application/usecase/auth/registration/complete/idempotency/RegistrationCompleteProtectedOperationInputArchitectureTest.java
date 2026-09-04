package com.bct.ngtpa.apiservice.application.usecase.auth.registration.complete.idempotency;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class RegistrationCompleteProtectedOperationInputArchitectureTest {
  private static final Path SOURCE =
      Path.of(
          "src/main/java/com/bct/ngtpa/apiservice/application/usecase/auth/registration/complete/"
              + "idempotency/RegistrationCompleteProtectedOperationInput.java");

  @Test
  void inputIsFrameworkPersistenceAndIdempotencyNeutral() throws Exception {
    String source = Files.readString(SOURCE);
    assertThat(source)
        .contains("record RegistrationCompleteProtectedOperationInput")
        .contains("CompleteRegistrationCommand command")
        .contains("RegistrationAuthFlowContext registrationContext")
        .contains("UsernameCheckState usernameCheckState")
        .contains("AuthFlowSession refreshedSession")
        .doesNotContain(
            "RegistrationIdempotencyPort",
            "RegistrationRequestHashGenerator",
            "IdempotencyExecutor",
            "IdempotencyPort",
            "IdempotencyCommands",
            "adapter.out.redis",
            "org.springframework",
            "jakarta.");
  }

  @Test
  void stringRepresentationNeverDelegatesToProtectedComponents() throws Exception {
    String source = Files.readString(SOURCE);
    String toStringBody = source.substring(source.indexOf("public String toString()"));
    assertThat(toStringBody)
        .contains("<redacted>")
        .doesNotContain(
            "command.toString()",
            "registrationContext.toString()",
            "usernameCheckState.toString()",
            "refreshedSession.toString()");
  }
}
