package com.bct.ngtpa.apiservice.adapter.in.web.pageconfig;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class PersonalInformationConfirmationDisplayYamlTest {

    private static final Path PERSONAL_INFORMATION_YAML = Path.of(
            "src/main/resources/application-page-personal-information.yml");

    @Test
    void confirmationDisplayTextIsCentralizedAndReferencedByCodes() throws IOException {
        String yaml = Files.readString(PERSONAL_INFORMATION_YAML);

        assertThat(yaml).contains("'[personalInformation.confirmation.title]':");
        assertThat(yaml).contains("'[personalInformation.confirmation.reviewMessage]':");
        assertThat(yaml).contains("'[personalInformation.confirmation.beforeLabel]':");
        assertThat(yaml).contains("'[personalInformation.confirmation.afterLabel]':");
        assertThat(yaml).contains("'[personalInformation.confirmation.securityVerification.label]':");
        assertThat(yaml).contains("'[personalInformation.confirmation.securityVerification.instruction]':");

        assertThat(yaml).contains("titleCode: personalInformation.confirmation.title");
        assertThat(yaml).contains("reviewMessageCode: personalInformation.confirmation.reviewMessage");
        assertThat(yaml).contains("beforeLabelCode: personalInformation.confirmation.beforeLabel");
        assertThat(yaml).contains("afterLabelCode: personalInformation.confirmation.afterLabel");
        assertThat(yaml).contains("labelCode: personalInformation.confirmation.securityVerification.label");
        assertThat(yaml).contains("instructionCode: personalInformation.confirmation.securityVerification.instruction");
    }
}
