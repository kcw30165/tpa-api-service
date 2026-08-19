package com.bct.ngtpa.apiservice.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class StartRegistrationProtectedContextGuardTest {

  @Test
  void startRegistrationPassesRegistrationContextToSessionGateway() throws Exception {
    Path sourcePath =
        Path.of(
            "src/main/java/com/bct/ngtpa/apiservice/application/usecase/auth/registration/StartRegistrationService.java");
    assertThat(Files.exists(sourcePath)).isTrue();
    String source = Files.readString(sourcePath);
    assertThat(source)
        .containsIgnoringWhitespaces(
            "new RegistrationAuthFlowContext",
            "validated.identityType()",
            "validated.identityValue()",
            "validated.dateOfBirth()",
            "validated.contactType()",
            "validated.contactValue()",
            "createRegistrationSession(validated.maskedDestination(), validated.contactType(), registrationContext)");
    assertThat(source)
        .doesNotContain(
            "return sessionGateway.createRegistrationSession(validated.maskedDestination(), validated.contactType())");
  }
}
