package com.bct.ngtpa.apiservice.adapter.in.web.validation;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class PersonalInformationFieldPresenceValidationSemanticsGuardTest {

    private static final Path VALIDATOR = Path.of("src/main/java/com/bct/ngtpa/apiservice/adapter/in/web/validation/PersonalInformationUpdateYamlValidator.java");
    private static final Path YAML = Path.of("src/main/resources/application-page-personal-information.yml");

    @Test
    void optionalFieldValidationRunsWhenFieldIsSubmittedEvenIfBlank() throws Exception {
        String source = Files.readString(VALIDATOR);
        assertThat(source)
                .contains("submittedFields.containsKey(field.getId())")
                .contains("boolean fieldSubmitted")
                .contains("&& !fieldSubmitted")
                .doesNotContain("!\"required\".equals(type) && isBlankValue(value)");
    }

    @Test
    void fixedLengthHongKongPhoneFieldsHaveMinAndMaxDslRules() throws Exception {
        String yaml = Files.readString(YAML);
        assertFixedLength(yaml, "hongKongBusinessPhone", 8);
        assertFixedLength(yaml, "homeTel", 8);
        assertFixedLength(yaml, "hongKongMobilePhone", 8);
    }

    private void assertFixedLength(String yaml, String fieldId, int length) {
        String block = fieldBlock(yaml, fieldId);
        assertThat(block)
                .contains("id: " + fieldId + ".minLength")
                .contains("type: minLength")
                .contains("value: " + length)
                .contains("id: " + fieldId + ".maxLength")
                .contains("type: maxLength")
                .contains("value: " + length);
    }

    private String fieldBlock(String yaml, String fieldId) {
        int start = yaml.indexOf("          - id: " + fieldId + "\n");
        assertThat(start).as(fieldId + " field start").isGreaterThanOrEqualTo(0);
        int nextField = yaml.indexOf("\n          - id:", start + 1);
        int nextSectionTitle = yaml.indexOf("\n          titleCode:", start + 1);
        int end = yaml.length();
        if (nextField >= 0) end = Math.min(end, nextField);
        if (nextSectionTitle >= 0) end = Math.min(end, nextSectionTitle);
        return yaml.substring(start, end);
    }
}
