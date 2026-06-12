package com.bct.ngtpa.apiservice.adapter.in.web.pageconfig;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

class PersonalInformationDisplayCentralizationGuardTest {

    private static final Path PERSONAL_INFORMATION_YAML = Path.of(
            "src/main/resources/application-page-personal-information.yml");
    private static final Path PERSONAL_INFORMATION_MAPPER = Path.of(
            "src/main/java/com/bct/ngtpa/apiservice/adapter/in/web/mapper/PersonalInformationWebMapper.java");
    private static final Path PAGE_DISPLAY_TEXT_RESOLVER = Path.of(
            "src/main/java/com/bct/ngtpa/apiservice/adapter/in/web/mapper/PageDisplayTextResolver.java");

    @Test
    void personalInformationYamlDoesNotContainSupportedInlineDisplayFallbacks() throws IOException {
        Map<String, Object> root = loadYaml();
        Map<String, Object> page = personalInformationPage(root);

        List<String> violations = new ArrayList<>();
        assertNoInlineDisplay(page.get("metadata"), "metadata", violations, "title");
        assertNoInlineDisplay(page.get("confirmation"), "confirmation", violations,
                "title", "reviewMessage", "beforeLabel", "afterLabel");

        Map<String, Object> form = asMap(page.get("form"));
        forEachMap(form.get("actions"), "form.actions", (action, path) ->
                assertNoInlineDisplay(action, path, violations, "label"));
        forEachMap(form.get("sections"), "form.sections", (section, sectionPath) -> {
            assertNoInlineDisplay(section, sectionPath, violations, "title");
            forEachMap(section.get("fields"), sectionPath + ".fields", (field, fieldPath) -> {
                assertNoInlineDisplay(field, fieldPath, violations, "label", "placeholder");
                forEachMap(field.get("validations"), fieldPath + ".validations", (rule, rulePath) ->
                        assertNoInlineDisplay(rule, rulePath, violations, "message"));
            });
        });

        Map<String, Object> confirmation = asMap(page.get("confirmation"));
        Map<String, Object> securityVerification = asMap(confirmation.get("securityVerification"));
        assertNoInlineDisplay(securityVerification, "confirmation.securityVerification", violations, "label", "instruction");

        assertThat(violations).as("inline display fallbacks must stay centralized in personalInformation.display")
                .isEmpty();
    }

    @Test
    void personalInformationDisplayContainsRequiredCentralEntriesAndStableVariantContract() throws IOException {
        Map<String, Object> page = personalInformationPage(loadYaml());
        Map<String, Object> display = normalizeDisplayKeys(asMap(page.get("display")));

        assertThat(display).containsKeys(
                "personalInformation.page.title",
                "personalInformation.action.submit.label",
                "personalInformation.section.addressInformation.title",
                "personalInformation.field.emailAddress.label",
                "personalInformation.address.poBoxBlocked.message",
                "personalInformation.address.poBoxBlocked.message.JP",
                "personalInformation.confirmation.title",
                "personalInformation.confirmation.reviewMessage",
                "personalInformation.confirmation.beforeLabel",
                "personalInformation.confirmation.afterLabel",
                "personalInformation.confirmation.securityVerification.label",
                "personalInformation.confirmation.securityVerification.instruction");

        String yaml = Files.readString(PERSONAL_INFORMATION_YAML);
        assertThat(yaml).contains("messageCode: personalInformation.address.poBoxBlocked.message");
        assertThat(yaml).doesNotContain("messageCode: personalInformation.address.poBoxBlocked.message.JP");
        assertThat(yaml).doesNotContain("variants:");
    }

    @Test
    void mapperAndResolverKeepConfirmedCentralizedDisplayPath() throws IOException {
        String mapper = Files.readString(PERSONAL_INFORMATION_MAPPER);
        String resolver = Files.readString(PAGE_DISPLAY_TEXT_RESOLVER);

        assertThat(mapper).contains("buildConfirmation(pageSchema, language, accountEnv, trustCode, schemeType)");
        assertThat(mapper).contains("resolvePageDisplayText(");
        assertThat(mapper).contains("confirmation.getTitleCode()");
        assertThat(mapper).contains("confirmation.getReviewMessageCode()");
        assertThat(mapper).contains("stringValue(securityVerification.get(\"labelCode\"))");
        assertThat(mapper).doesNotContain("yamlResponseMapper.toResponseMap(pageSchema != null ? pageSchema.getConfirmation() : null, language)");

        assertThat(resolver).contains("resolveCodeFirstDisplay(display, candidate, languageKey)");
        assertThat(resolver).contains("candidateKeys(code, accountEnv, trustCode, schemeType, languageKey)");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> loadYaml() throws IOException {
        return new Yaml().loadAs(Files.readString(PERSONAL_INFORMATION_YAML), Map.class);
    }

    private static Map<String, Object> personalInformationPage(Map<String, Object> root) {
        return asMap(asMap(asMap(root.get("bff-pages")).get("pages")).get("personalInformation"));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object value) {
        assertThat(value).isInstanceOf(Map.class);
        return (Map<String, Object>) value;
    }

    @SuppressWarnings("unchecked")
    private static void forEachMap(Object value, String path, MapVisitor visitor) {
        if (value == null) {
            return;
        }
        assertThat(value).as(path).isInstanceOf(List.class);
        List<Object> items = (List<Object>) value;
        for (int i = 0; i < items.size(); i++) {
            Object item = items.get(i);
            assertThat(item).as(path + "[" + i + "]").isInstanceOf(Map.class);
            visitor.visit((Map<String, Object>) item, path + "[" + i + "]");
        }
    }

    private static void assertNoInlineDisplay(Object value, String path, List<String> violations, String... keys) {
        if (value == null) {
            return;
        }
        Map<String, Object> map = asMap(value);
        for (String key : keys) {
            if (map.get(key) instanceof Map<?, ?>) {
                violations.add(path + "." + key);
            }
        }
    }


    private static Map<String, Object> normalizeDisplayKeys(Map<String, Object> display) {
        Map<String, Object> normalized = new LinkedHashMap<>();
        for (var entry : display.entrySet()) {
            String key = entry.getKey();
            if (key != null && key.startsWith("[") && key.endsWith("]")) {
                key = key.substring(1, key.length() - 1);
            }
            normalized.put(key, entry.getValue());
        }
        return normalized;
    }

    @FunctionalInterface
    private interface MapVisitor {
        void visit(Map<String, Object> map, String path);
    }
}
