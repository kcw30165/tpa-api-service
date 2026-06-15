package com.bct.ngtpa.apiservice.adapter.in.web.pageconfig;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

class PersonalInformationPatternValidationYamlRuntimeGuardTest {

    // curl regression: field-level pattern metadata must remain absent; validations[] is canonical.

    @Test
    void phoneAndFaxPatternRulesAreCentralizedInValidationDslOnly() {
        for (String fieldId : List.of(
                "hongKongBusinessPhone",
                "hongKongBusinessPhoneExtension",
                "homeTel",
                "hongKongMobilePhone",
                "faxNo",
                "overseasAreaCode",
                "overseasPhoneNumber")) {
            Map<String, Object> field = field(fieldId);

            assertThat(field)
                    .as("Do not duplicate executable regex at field.pattern for " + fieldId)
                    .doesNotContainKey("pattern");

            List<Map<String, Object>> patternRules = validations(field).stream()
                    .filter(rule -> "pattern".equals(rule.get("type")))
                    .toList();
            assertThat(patternRules)
                    .as("Expected at least one explicit pattern validation for " + fieldId)
                    .isNotEmpty();
            assertThat(patternRules)
                    .as("Every pattern validation for " + fieldId + " must declare a non-blank value")
                    .allSatisfy(rule -> assertThat(rule.get("value"))
                            .as("pattern value for rule " + rule.get("id"))
                            .isNotNull()
                            .asString()
                            .isNotBlank());
        }
    }

    @Test
    void curlPayloadPhoneValuesMatchConfiguredPatterns() {
        assertMatches("hongKongBusinessPhone", "21234567");
        assertMatches("hongKongMobilePhone", "98765432");
        assertMatches("overseasAreaCode", "212");
        assertMatches("overseasPhoneNumber", "5550123");
    }

    private void assertMatches(String fieldId, String value) {
        List<Map<String, Object>> patternRules = validations(field(fieldId)).stream()
                .filter(rule -> "pattern".equals(rule.get("type")))
                .toList();
        assertThat(patternRules).isNotEmpty();
        for (Map<String, Object> rule : patternRules) {
            assertThat(value)
                    .as(fieldId + " should match " + rule.get("id") + " / " + rule.get("value"))
                    .matches(rule.get("value").toString());
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> page() {
        try (InputStream inputStream = getClass().getClassLoader()
                .getResourceAsStream("application-page-personal-information.yml")) {
            assertThat(inputStream).as("application-page-personal-information.yml must be on test classpath").isNotNull();
            Map<String, Object> root = new Yaml().load(inputStream);
            return (Map<String, Object>) ((Map<String, Object>) ((Map<String, Object>) root.get("bff-pages"))
                    .get("pages")).get("personalInformation");
        } catch (Exception exception) {
            throw new AssertionError("Failed to load personal-information YAML", exception);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> field(String fieldId) {
        Map<String, Object> form = (Map<String, Object>) page().get("form");
        for (Map<String, Object> section : (List<Map<String, Object>>) form.get("sections")) {
            for (Map<String, Object> field : (List<Map<String, Object>>) section.get("fields")) {
                if (fieldId.equals(field.get("id"))) {
                    return field;
                }
            }
        }
        throw new AssertionError("Field not found in personal-information YAML: " + fieldId);
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> validations(Map<String, Object> owner) {
        Object value = owner.get("validations");
        return value instanceof List<?> list ? (List<Map<String, Object>>) list : List.of();
    }
}
