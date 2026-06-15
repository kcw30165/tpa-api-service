package com.bct.ngtpa.apiservice.adapter.in.web.pageconfig;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ValidationDslBindingAndLinterTest {

    @Test
    void validationRulePropertiesExposeValueByVariantForExecutableRuleValues() {
        ValidationRuleProperties rule = new ValidationRuleProperties();
        Map<String, Object> variants = new LinkedHashMap<>();
        variants.put("JP", 11);

        rule.setValue(1);
        rule.setValueByVariant(variants);

        assertThat(rule.getValue()).isEqualTo(1);
        assertThat(rule.getValueByVariant()).containsEntry("JP", 11);
    }

    @Test
    void linterAcceptsValidDslRulesIncludingGroupsTargetsAndValueByVariant() {
        ValidationRuleProperties emailMinLength = rule("email.minLength", "minLength");
        emailMinLength.setValue(1);
        emailMinLength.setValueByVariant(Map.of("JP", 11));

        ValidationRuleProperties groupRule = rule("address.atLeastOneRequired", "groupRequirement");
        RuleConditionProperties when = new RuleConditionProperties();
        when.setOperator("allGroupsEmpty");
        when.setGroups(List.of(
                Map.of("name", "residentialAddress", "fields", List.of("residentialAddressLine1", "residentialCountry")),
                Map.of("name", "mailingAddress", "fields", List.of("mailingAddressLine1", "mailingCountry"))));
        RuleActionProperties then = new RuleActionProperties();
        then.setOperator("fail");
        then.setTargets(List.of("residentialAddressLine1", "mailingAddressLine1"));
        groupRule.setWhen(when);
        groupRule.setThen(then);

        BffPagesProperties properties = propertiesWith(List.of(emailMinLength), List.of(groupRule));

        assertThat(PageSchemaLinter.lint(properties).getErrors()).isEmpty();
    }

    @Test
    void linterRejectsUnsupportedValidationType() {
        ValidationRuleProperties rule = rule("bad.type", "notSupported");

        assertThat(PageSchemaLinter.lint(propertiesWith(List.of(rule), List.of())).getErrors())
                .anyMatch(error -> error.contains("unsupported validation type notSupported"));
    }

    @Test
    void linterRejectsUnknownThenTargetsAndGroupFields() {
        ValidationRuleProperties rule = rule("bad.references", "groupRequirement");
        RuleConditionProperties when = new RuleConditionProperties();
        when.setOperator("allGroupsEmpty");
        when.setGroups(List.of(Map.of("name", "badGroup", "fields", List.of("missingGroupField"))));
        RuleActionProperties then = new RuleActionProperties();
        then.setOperator("fail");
        then.setTargets(List.of("missingTarget"));
        rule.setWhen(when);
        rule.setThen(then);

        assertThat(PageSchemaLinter.lint(propertiesWith(List.of(), List.of(rule))).getErrors())
                .anySatisfy(error -> assertThat(error).contains("missingGroupField"))
                .anySatisfy(error -> assertThat(error).contains("missingTarget"));
    }

    @Test
    void linterRejectsInvalidExecutableValues() {
        ValidationRuleProperties blockedAddress = rule("address.poBox", "blockedAddress");
        blockedAddress.setValue(" ");

        ValidationRuleProperties minLength = rule("email.minLength", "minLength");
        minLength.setValue("abc");
        minLength.setValueByVariant(Map.of("JP", "xyz"));

        ValidationRuleProperties blankVariantKey = rule("email.maxLength", "maxLength");
        blankVariantKey.setValue(120);
        blankVariantKey.setValueByVariant(Map.of(" ", 121));

        assertThat(PageSchemaLinter.lint(propertiesWith(List.of(blockedAddress, minLength, blankVariantKey), List.of())).getErrors())
                .anyMatch(error -> error.contains("blockedAddress rule address.poBox requires non-blank value"))
                .anyMatch(error -> error.contains("minLength rule email.minLength requires numeric value"))
                .anyMatch(error -> error.contains("minLength rule email.minLength requires numeric valueByVariant value for JP"))
                .anyMatch(error -> error.contains("blank valueByVariant key"));
    }

    private ValidationRuleProperties rule(String id, String type) {
        ValidationRuleProperties rule = new ValidationRuleProperties();
        rule.setId(id);
        rule.setType(type);
        rule.setSeverity("error");
        rule.setCode("personalInformation." + id);
        rule.setMessageCode("personalInformation." + id + ".message");
        return rule;
    }

    private BffPagesProperties propertiesWith(
            List<ValidationRuleProperties> fieldRules,
            List<ValidationRuleProperties> pageRules) {
        FieldProperties residentialAddressLine1 = field("residentialAddressLine1", fieldRules);
        FieldProperties mailingAddressLine1 = field("mailingAddressLine1", List.of());
        FieldProperties residentialCountry = field("residentialCountry", List.of());
        FieldProperties mailingCountry = field("mailingCountry", List.of());
        FieldProperties emailAddress = field("emailAddress", List.of());

        SectionProperties section = new SectionProperties();
        section.setId("testSection");
        section.setFields(List.of(
                residentialAddressLine1,
                mailingAddressLine1,
                residentialCountry,
                mailingCountry,
                emailAddress));

        FormMetadataProperties form = new FormMetadataProperties();
        form.setSections(List.of(section));

        PageSchemaProperties page = new PageSchemaProperties();
        page.setForm(form);
        page.setValidations(pageRules);

        BffPagesProperties properties = new BffPagesProperties();
        properties.setPages(Map.of("personalInformation", page));
        return properties;
    }

    private FieldProperties field(String id, List<ValidationRuleProperties> rules) {
        FieldProperties field = new FieldProperties();
        field.setId(id);
        field.setValidations(rules);
        return field;
    }
}
