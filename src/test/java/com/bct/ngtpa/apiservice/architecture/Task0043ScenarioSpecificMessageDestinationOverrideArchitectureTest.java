package com.bct.ngtpa.apiservice.architecture;

import static org.assertj.core.api.Assertions.assertThat;
import java.nio.file.*;
import org.junit.jupiter.api.Test;

class Task0043ScenarioSpecificMessageDestinationOverrideArchitectureTest {
    @Test
    void reusesExistingMessageAndDestinationOverrideTypes() throws Exception {
        String p = Files.readString(Path
                .of("src/main/java/com/bct/ngtpa/apiservice/adapter/in/web/auth/config/AuthWorkflowProperties.java"));
        assertThat(p).contains("private final Message message = new Message();")
                .doesNotContain("LoginDestinationOverride", "LoginMessage");
    }

    @Test
    void yamlSeparatesRegistrationAndLoginOverrideBranches() throws Exception {
        String y = Files.readString(Path.of("src/main/resources/application.yml"));
        assertThat(y).contains("REGISTRATION_MESSAGE_OVERRIDE_MOBILE", "REGISTRATION_MESSAGE_OVERRIDE_EMAIL",
                "LOGIN_MESSAGE_OVERRIDE_MOBILE", "LOGIN_MESSAGE_OVERRIDE_EMAIL");
    }
}
