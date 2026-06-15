package com.bct.ngtpa.apiservice.adapter.in.web.pageconfig;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class PersonalInformationDisplayVariantYamlTest {

    private static final Path PERSONAL_INFORMATION_YAML = Path.of(
            "src/main/resources/application-page-personal-information.yml");

    @Test
    void poBoxBlockedKeepsStableMessageCodeAndAddsJpDisplayVariant() throws IOException {
        String yaml = Files.readString(PERSONAL_INFORMATION_YAML);

        assertThat(yaml).containsPattern("['\"]\\[personalInformation.address.poBoxBlocked.message\\]['\"]:");
        assertThat(yaml).containsPattern("['\"]\\[personalInformation.address.poBoxBlocked.message.JP\\]['\"]:");
        assertThat(yaml).contains("code: personalInformation.address.poBoxBlocked");
        assertThat(yaml).contains("messageCode: personalInformation.address.poBoxBlocked.message");
        assertThat(yaml).doesNotContain("messageCode: personalInformation.address.poBoxBlocked.message.JP");
    }
}
