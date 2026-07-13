package com.bct.ngtpa.apiservice.documentation;

import static org.assertj.core.api.Assertions.assertThat;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class RegistrationMessageDestinationOverrideReadmeTest {
    @Test
    void readmeDocumentsEnvironmentConfigurationAndSafetyContract() throws Exception {
        String readme = Files.readString(Path.of("README.md"));
        assertThat(readme)
                .contains("### Registration OTP Destination Override")
                .contains("DeploymentEnvironmentPolicy")
                .contains("REGISTRATION_MESSAGE_OVERRIDE_MOBILE")
                .contains("REGISTRATION_MESSAGE_OVERRIDE_EMAIL")
                .contains("There is no enable flag")
                .contains("no fallback to the original recipient")
                .contains("masked destination shown to the frontend")
                .contains("destination hashing")
                .contains("rate limiting")
                .contains("Do not commit real destination values");
    }

    @Test
    void readmeUsesCurrentReferenceDateAdapterName() throws Exception {
        String readme = Files.readString(Path.of("README.md"));
        assertThat(readme).contains("ReferenceDateAdapter");
        assertThat(readme).doesNotContain("NonpReferenceDateAdapter");
    }
}
