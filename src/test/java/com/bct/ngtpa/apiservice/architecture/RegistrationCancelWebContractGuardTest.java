package com.bct.ngtpa.apiservice.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class RegistrationCancelWebContractGuardTest {
    private static final Path CONTROLLER = Path.of(
            "src/main/java/com/bct/ngtpa/apiservice/adapter/in/web/controller/"
                    + "AuthRegistrationController.java");
    private static final Path YAML = Path.of(
            "src/main/resources/application-page-registration.yml");

    @Test
    void existingRegistrationControllerOwnsCancelEndpointAndKeepsOneConstructor() throws Exception {
        String source = Files.readString(CONTROLLER);
        assertThat(source)
                .contains("@PostMapping(path = \"/cancel\"")
                .contains("CancelRegistrationUseCase")
                .contains("clearAuthFlowCookie(response)")
                .contains("clearXsrfCookie(response)");
        long explicitConstructors = Pattern.compile("public\\s+AuthRegistrationController\\s*\\(")
                .matcher(source).results().count();
        assertThat(explicitConstructors).isLessThanOrEqualTo(1);
    }

    @Test
    void cancellationPageIsStandaloneAndHasNoVariant() throws Exception {
        String yaml = Files.readString(YAML);
        int start = yaml.indexOf("registration-cancelled:");
        int next = yaml.indexOf("registration-success:", start);
        assertThat(start).isGreaterThanOrEqualTo(0);
        assertThat(next).isGreaterThan(start);
        String cancellationPage = yaml.substring(start, next);
        assertThat(cancellationPage)
                .contains("id: registration-cancelled")
                .contains("returnToLogin")
                .contains("nextStep: RETURN_TO_LOGIN")
                .doesNotContain("variants:")
                .doesNotContain("variant:");
    }
}
