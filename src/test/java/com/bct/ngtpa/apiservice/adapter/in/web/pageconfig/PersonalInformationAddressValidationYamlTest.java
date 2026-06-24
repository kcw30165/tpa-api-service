package com.bct.ngtpa.apiservice.adapter.in.web.pageconfig;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

class PersonalInformationAddressValidationYamlTest {

    private static final String PO_BOX_TERMS = "PO Box~|~P.O. Box~|~P O Box~|~P.O Box~|~Post Office Box~|~P.O. Box~|~郵政信箱";

    @Test
    void addressDependencyRulesUseDistinctResidentialMailingAndOverseasCountryTargets() {
        Map<String, Object> page = personalInformationPage();
        List<Map<String, Object>> validations = validations(page);

        Map<String, Object> residentialCountryRule = rule(validations, "residentialCountry.requiredWhenResidentialAddressCaptured");
        assertThat(fields(when(residentialCountryRule)))
                .containsExactly("residentialAddressLine1", "residentialAddressLine2", "residentialAddressLine3");
        assertThat(targets(then(residentialCountryRule))).containsExactly("residentialCountry");

        Map<String, Object> mailingCountryRule = rule(validations, "mailingCountry.requiredWhenMailingAddressCaptured");
        assertThat(fields(when(mailingCountryRule)))
                .containsExactly("mailingAddressLine1", "mailingAddressLine2", "mailingAddressLine3");
        assertThat(targets(then(mailingCountryRule))).containsExactly("mailingCountry");

        Map<String, Object> overseasCountryRule = rule(validations, "overseasCountryCode.requiredWhenOverseasPhonePresent");
        assertThat(fields(when(overseasCountryRule))).containsExactly("overseasPhoneNumber");
        assertThat(targets(then(overseasCountryRule))).containsExactly("overseasCountryCode");
    }

    @Test
    void atLeastOneAddressRuleUsesResidentialAndMailingGroupsOnly() {
        Map<String, Object> rule = rule(validations(personalInformationPage()), "address.atLeastOneRequired");
        List<Map<String, Object>> groups = groups(when(rule));

        assertThat(groups).extracting(group -> group.get("name"))
                .containsExactly("residentialAddress", "mailingAddress");
        assertThat(fields(groups.get(0)))
                .containsExactly("residentialAddressLine1", "residentialAddressLine2", "residentialAddressLine3", "residentialCountry");
        assertThat(fields(groups.get(1)))
                .containsExactly("mailingAddressLine1", "mailingAddressLine2", "mailingAddressLine3", "mailingCountry");
        assertThat(targets(then(rule))).containsExactly("residentialAddressLine1", "mailingAddressLine1");
    }

    @Test
    void mailingOnlyAddressMustNotBeBlockedByUnconditionalResidentialAddressRequiredRule() {
        Map<String, Object> residentialAddressLine1 = field("residentialAddressLine1");

        assertThat(validations(residentialAddressLine1))
                .filteredOn(rule -> "required".equalsIgnoreCase(string(rule.get("type"))))
                .as("AC4 requires residential address to become optional when a valid mailing address is supplied; use page-level groupRequirement/conditionalRequired instead of unconditional residential required")
                .isEmpty();
    }

    @Test
    void countryFieldsMustNotHaveUnconditionalRequiredRulesBecauseCountryIsRequiredOnlyWhenItsGroupIsCaptured() {
        assertThat(validations(field("residentialCountry")))
                .filteredOn(rule -> "required".equalsIgnoreCase(string(rule.get("type"))))
                .as("residentialCountry must be required by residentialCountry.requiredWhenResidentialAddressCaptured only")
                .isEmpty();
        assertThat(validations(field("mailingCountry")))
                .filteredOn(rule -> "required".equalsIgnoreCase(string(rule.get("type"))))
                .as("mailingCountry must be required by mailingCountry.requiredWhenMailingAddressCaptured only")
                .isEmpty();
    }

    @Test
    void addressLineOneMinLengthAndPoBoxRulesAreConfiguredFromYaml() {
        assertMinLengthRule("residentialAddressLine1", "personalInformation.residentialAddress.invalid");

        for (String fieldId : List.of(
                "residentialAddressLine1", "residentialAddressLine2", "residentialAddressLine3")) {
            assertThat(validations(field(fieldId)))
                    .filteredOn(rule -> "blockedAddress".equalsIgnoreCase(string(rule.get("type"))))
                    .anySatisfy(rule -> {
                        assertThat(rule.get("value")).isEqualTo(PO_BOX_TERMS);
                        assertThat(rule.get("code")).isEqualTo("personalInformation.address.poBoxBlocked");
                        assertThat(rule.get("messageCode")).isEqualTo("personalInformation.address.poBoxBlocked.message");
                    });
        }
    }

    private void assertMinLengthRule(String fieldId, String expectedCode) {
        assertThat(validations(field(fieldId)))
                .filteredOn(rule -> "minLength".equalsIgnoreCase(string(rule.get("type"))))
                .anySatisfy(rule -> {
                    assertThat(rule.get("value")).isEqualTo(5);
                    assertThat(rule.get("code")).isEqualTo(expectedCode);
                });
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> personalInformationPage() {
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
        Map<String, Object> form = (Map<String, Object>) personalInformationPage().get("form");
        for (Map<String, Object> section : (List<Map<String, Object>>) form.get("sections")) {
            for (Map<String, Object> field : (List<Map<String, Object>>) section.get("fields")) {
                if (fieldId.equals(field.get("id"))) {
                    return field;
                }
            }
        }
        throw new AssertionError("Field not found in personal-information YAML: " + fieldId);
    }

    private Map<String, Object> rule(List<Map<String, Object>> rules, String ruleId) {
        return rules.stream()
                .filter(rule -> ruleId.equals(rule.get("id")))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Validation rule not found: " + ruleId));
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> validations(Map<String, Object> owner) {
        Object value = owner.get("validations");
        return value instanceof List<?> list ? (List<Map<String, Object>>) list : List.of();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> when(Map<String, Object> rule) {
        return (Map<String, Object>) rule.get("when");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> then(Map<String, Object> rule) {
        return (Map<String, Object>) rule.get("then");
    }

    @SuppressWarnings("unchecked")
    private List<String> fields(Map<String, Object> node) {
        Object value = node.get("fields");
        return value instanceof List<?> list ? (List<String>) list : List.of();
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> groups(Map<String, Object> node) {
        Object value = node.get("groups");
        return value instanceof List<?> list ? (List<Map<String, Object>>) list : List.of();
    }

    @SuppressWarnings("unchecked")
    private List<String> targets(Map<String, Object> node) {
        Object value = node.get("targets");
        return value instanceof List<?> list ? (List<String>) list : List.of();
    }

    private String string(Object value) {
        return value == null ? "" : value.toString();
    }
}
