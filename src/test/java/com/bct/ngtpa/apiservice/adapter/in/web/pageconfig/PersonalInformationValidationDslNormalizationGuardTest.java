package com.bct.ngtpa.apiservice.adapter.in.web.pageconfig;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

class PersonalInformationValidationDslNormalizationGuardTest {

    @Test
    void everyFieldLevelMaxLengthHasExecutableDslRule() {
        Map<String, Object> page = page();
        Map<String, Integer> expected = Map.ofEntries(
                Map.entry("residentialAddressLine1", 40),
                Map.entry("residentialAddressLine2", 40),
                Map.entry("residentialAddressLine3", 40),
                Map.entry("mailingAddressLine1", 40),
                Map.entry("mailingAddressLine2", 40),
                Map.entry("mailingAddressLine3", 40),
                Map.entry("hongKongBusinessPhone", 8),
                Map.entry("hongKongBusinessPhoneExtension", 4),
                Map.entry("homeTel", 8),
                Map.entry("hongKongMobilePhone", 8),
                Map.entry("overseasPhoneNumber", 17),
                Map.entry("overseasPhoneExtension", 4),
                Map.entry("emailAddress", 120));

        expected.forEach((fieldId, value) -> {
            Map<String, Object> rule = validations(field(fieldId, page)).stream()
                    .filter(candidate -> (fieldId + ".maxLength").equals(candidate.get("id")))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError(fieldId + ".maxLength not found"));
            assertThat(rule.get("type")).isEqualTo("maxLength");
            assertThat(rule.get("value")).isEqualTo(value);
            assertThat(rule.get("severity")).isEqualTo("error");
        });
    }

    @Test
    void executableFieldValidationRulesAreNamed() {
        Map<String, Object> page = page();
        for (Map<String, Object> section : sections(page)) {
            for (Map<String, Object> field : fields(section)) {
                for (Map<String, Object> rule : validations(field)) {
                    String type = String.valueOf(rule.get("type"));
                    if (List.of("required", "email", "minLength", "maxLength", "pattern", "blockedAddress", "noSpaces")
                            .contains(type)) {
                        assertThat(rule.get("id"))
                                .as("Executable validation rule must be named for field " + field.get("id") + ": " + rule)
                                .isNotNull();
                    }
                }
            }
        }
    }

    @Test
    void typoFieldPatternMetadataIsNotPresent() {
        assertThat(field("emailAddress", page())).doesNotContainKey("patter");
    }

    @Test
    void confirmationPasswordValidationsAreNamedAndExecutable() {
        Map<String, Object> passwordField = passwordField(page());
        assertThat(validations(passwordField))
                .extracting(rule -> rule.get("id"))
                .containsExactly("password.required", "password.minLength", "password.maxLength", "password.noSpaces");
        assertThat(validations(passwordField).stream()
                        .filter(rule -> "password.minLength".equals(rule.get("id")))
                        .findFirst()
                        .orElseThrow()
                        .get("value"))
                .isEqualTo(8);
        assertThat(validations(passwordField).stream()
                        .filter(rule -> "password.maxLength".equals(rule.get("id")))
                        .findFirst()
                        .orElseThrow()
                        .get("value"))
                .isEqualTo(16);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> page() {
        try (InputStream inputStream = getClass().getClassLoader()
                .getResourceAsStream("application-page-personal-information.yml")) {
            assertThat(inputStream).isNotNull();
            Map<String, Object> root = new Yaml().load(inputStream);
            return (Map<String, Object>) ((Map<String, Object>) ((Map<String, Object>) root.get("bff-pages"))
                    .get("pages")).get("personalInformation");
        } catch (Exception exception) {
            throw new AssertionError("Failed to load personal-information YAML", exception);
        }
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> sections(Map<String, Object> page) {
        return (List<Map<String, Object>>) ((Map<String, Object>) page.get("form")).get("sections");
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> fields(Map<String, Object> section) {
        Object value = section.get("fields");
        return value instanceof List<?> list ? (List<Map<String, Object>>) list : List.of();
    }

    private Map<String, Object> field(String fieldId, Map<String, Object> page) {
        return sections(page).stream()
                .flatMap(section -> fields(section).stream())
                .filter(field -> fieldId.equals(field.get("id")))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Field not found: " + fieldId));
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> validations(Map<String, Object> owner) {
        Object value = owner.get("validations");
        return value instanceof List<?> list ? (List<Map<String, Object>>) list : List.of();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> passwordField(Map<String, Object> page) {
        return (Map<String, Object>) ((Map<String, Object>) ((Map<String, Object>) page.get("confirmation"))
                .get("securityVerification")).get("passwordField");
    }
}
