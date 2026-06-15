package com.bct.ngtpa.apiservice.adapter.in.web.validation;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

class PersonalInformationFieldPresenceValidationSemanticsGuardTest {

    private static final Path VALIDATOR = Path.of(
            "src/main/java/com/bct/ngtpa/apiservice/adapter/in/web/validation/PersonalInformationUpdateYamlValidator.java");
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
        Matcher fieldStart = Pattern.compile("(?m)^([ \\t]*)-\\s+id:\\s+" + Pattern.quote(fieldId) + "\\s*$")
                .matcher(yaml);
        int start = fieldStart.find() ? fieldStart.start() : -1;
        assertThat(start).as(fieldId + " field start").isGreaterThanOrEqualTo(0);
        String fieldIndent = fieldStart.group(1);
        Matcher nextFieldMatcher = Pattern.compile("(?m)^" + Pattern.quote(fieldIndent) + "-\\s+id:").matcher(yaml);
        int nextField = nextFieldMatcher.find(fieldStart.end()) ? nextFieldMatcher.start() : -1;
        String sectionIndent = fieldIndent.length() >= 2 ? fieldIndent.substring(0, fieldIndent.length() - 2) : "";
        Matcher nextSectionTitleMatcher = Pattern.compile("(?m)^" + Pattern.quote(sectionIndent) + "titleCode:")
                .matcher(yaml);
        int nextSectionTitle = nextSectionTitleMatcher.find(fieldStart.end()) ? nextSectionTitleMatcher.start() : -1;
        int end = yaml.length();
        if (nextField >= 0)
            end = Math.min(end, nextField);
        if (nextSectionTitle >= 0)
            end = Math.min(end, nextSectionTitle);
        return yaml.substring(start, end);
    }
}
