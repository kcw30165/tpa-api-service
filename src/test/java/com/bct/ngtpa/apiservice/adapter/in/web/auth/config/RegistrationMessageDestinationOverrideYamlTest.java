package com.bct.ngtpa.apiservice.adapter.in.web.auth.config;

import static org.assertj.core.api.Assertions.assertThat;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class RegistrationMessageDestinationOverrideYamlTest {
    @Test
    void applicationYaml_usesEnvironmentBackedRegistrationDestinations() throws Exception {
        String yaml = Files.readString(Path.of("src/main/resources/application.yml"));
        assertThat(yaml).contains("destination-override:")
                .contains("mobile: ${REGISTRATION_MESSAGE_OVERRIDE_MOBILE:}")
                .contains("email: ${REGISTRATION_MESSAGE_OVERRIDE_EMAIL:}");
    }
}
