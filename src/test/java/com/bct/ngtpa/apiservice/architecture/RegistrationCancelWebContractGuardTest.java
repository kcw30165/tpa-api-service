package com.bct.ngtpa.apiservice.architecture;

import static com.bct.ngtpa.apiservice.architecture.SourceTextAssertions.*;
import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class RegistrationCancelWebContractGuardTest {
  private static final Path CONTROLLER =
      Path.of(
          "src/main/java/com/bct/ngtpa/apiservice/adapter/in/web/auth/registration/"
              + "RegistrationController.java");
  private static final Path YAML = Path.of("src/main/resources/application-page-registration.yml");

  @Test
  void existingRegistrationControllerOwnsCancelEndpointAndKeepsOneConstructor() throws Exception {
    String source = Files.readString(CONTROLLER);
    assertThat(source)
        .containsIgnoringWhitespaces("@PostMapping(path = \"/cancel\"")
        .containsIgnoringWhitespaces("CancelRegistrationUseCase")
        .containsIgnoringWhitespaces("clearAuthFlowCookie(response)")
        .containsIgnoringWhitespaces("clearXsrfCookie(response)");
    long explicitConstructors =
        Pattern.compile("public\\s+RegistrationController\\s*\\(")
            .matcher(source)
            .results()
            .count();
    assertThat(explicitConstructors).isLessThanOrEqualTo(1);
  }

  @Test
  void cancellationPageIsStandaloneAndHasNoVariant() throws Exception {
    String yaml = Files.readString(YAML);
    int start = indexOfIgnoringWhitespace(yaml, "registration-cancelled:");
    int next = yaml.indexOf("registration-success:", start);
    assertThat(start).isGreaterThanOrEqualTo(0);
    assertThat(next).isGreaterThan(start);
    String cancellationPage = yaml.substring(start, next);
    assertThat(cancellationPage)
        .containsIgnoringWhitespaces("id: registration-cancelled")
        .containsIgnoringWhitespaces("returnToLogin")
        .containsIgnoringWhitespaces("nextStep: RETURN_TO_LOGIN")
        .doesNotContain("variants:")
        .doesNotContain("variant:");
  }
}
