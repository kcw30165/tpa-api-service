package com.bct.ngtpa.apiservice.adapter.in.web.pageconfig;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

class PageSchemaLinterTest {

    @Test
    void detectsUnsupportedOperatorsAndSeverity() {
        BffPagesProperties props = new BffPagesProperties();
        PageSchemaProperties page = new PageSchemaProperties();
        // create a field with invalid when/then/severity
        FieldProperties field = new FieldProperties();
        field.setId("f1");
        ValidationRuleProperties rule = new ValidationRuleProperties();
        rule.setId("r1");
        RuleConditionProperties when = new RuleConditionProperties();
        when.setOperator("unsupportedWhen");
        rule.setWhen(when);
        RuleActionProperties then = new RuleActionProperties();
        then.setOperator("unsupportedThen");
        rule.setThen(then);
        rule.setSeverity("badSeverity");
        field.setValidations(List.of(rule));

        SectionProperties section = new SectionProperties();
        section.setId("s1");
        section.setFields(List.of(field));

        FormMetadataProperties form = new FormMetadataProperties();
        form.setSections(List.of(section));
        page.setForm(form);

        props.getPages().put("p1", page);

        var result = PageSchemaLinter.lint(props);
        assertFalse(result.getErrors().isEmpty());
        assertTrue(result.getErrors().stream().anyMatch(e -> e.contains("unsupportedWhen")));
        assertTrue(result.getErrors().stream().anyMatch(e -> e.contains("unsupportedThen")));
        assertTrue(result.getErrors().stream().anyMatch(e -> e.contains("badSeverity")));
    }

    @Test
    void detectsMissingFieldReferences() {
        BffPagesProperties props = new BffPagesProperties();
        PageSchemaProperties page = new PageSchemaProperties();
        // rule references non-existent field
        ValidationRuleProperties rule = new ValidationRuleProperties();
        rule.setId("r1");
        RuleConditionProperties when = new RuleConditionProperties();
        when.setOperator("notBlank");
        when.setFields(List.of("no-such-field"));
        rule.setWhen(when);
        rule.setThen(new RuleActionProperties());
        rule.setSeverity("error");

        page.setValidations(List.of(rule));
        props.getPages().put("p1", page);

        var result = PageSchemaLinter.lint(props);
        assertFalse(result.getErrors().isEmpty());
        assertTrue(result.getErrors().stream().anyMatch(e -> e.contains("no-such-field")));
    }

    @Test
    void validSchemaProducesNoErrors() {
        BffPagesProperties props = new BffPagesProperties();
        PageSchemaProperties page = new PageSchemaProperties();
        FieldProperties field = new FieldProperties();
        field.setId("f1");
        ValidationRuleProperties rule = new ValidationRuleProperties();
        rule.setId("r1");
        RuleConditionProperties when = new RuleConditionProperties();
        when.setOperator("notBlank");
        when.setFields(List.of("f1"));
        rule.setWhen(when);
        RuleActionProperties then = new RuleActionProperties();
        then.setOperator("required");
        rule.setThen(then);
        rule.setSeverity("error");
        field.setValidations(List.of(rule));

        SectionProperties section = new SectionProperties();
        section.setId("s1");
        section.setFields(List.of(field));
        FormMetadataProperties form = new FormMetadataProperties();
        form.setSections(List.of(section));
        page.setForm(form);
        page.setValidations(List.of());

        props.getPages().put("p1", page);

        var result = PageSchemaLinter.lint(props);
        assertTrue(result.getErrors().isEmpty(), "Expected no lint errors but got: " + result.getErrors());
    }

    @Test
    void returnsCleanResultForNullPropertiesNullPagesNullPageAndPagesWithoutValidationContainers() {
        assertThat(PageSchemaLinter.lint(null).hasErrors()).isFalse();

        var noPages = new BffPagesProperties();
        noPages.setPages(null);
        assertThat(PageSchemaLinter.lint(noPages).hasErrors()).isFalse();

        var formWithoutSections = new FormMetadataProperties();
        formWithoutSections.setSections(null);
        var pageWithoutSections = new PageSchemaProperties();
        pageWithoutSections.setForm(formWithoutSections);

        var pageWithoutForm = new PageSchemaProperties();

        Map<String, PageSchemaProperties> pages = new LinkedHashMap<>();
        pages.put("nullPage", null);
        pages.put("withoutForm", pageWithoutForm);
        pages.put("withoutSections", pageWithoutSections);
        var props = new BffPagesProperties();
        props.setPages(pages);

        assertThat(PageSchemaLinter.lint(props).hasErrors()).isFalse();
    }

    @Test
    void skipsSectionsWithoutFieldsFieldsWithoutValidationsAndFieldsWithoutIds() {
        var sectionWithoutFields = new SectionProperties();
        sectionWithoutFields.setId("empty-section");
        sectionWithoutFields.setFields(null);

        var fieldWithoutId = new FieldProperties();
        fieldWithoutId.setId(null);
        fieldWithoutId.setValidations(null);

        var validField = new FieldProperties();
        validField.setId("known");
        validField.setValidations(null);

        var section = new SectionProperties();
        section.setId("section");
        section.setFields(List.of(fieldWithoutId, validField));

        var form = new FormMetadataProperties();
        form.setSections(List.of(sectionWithoutFields, section));

        var page = new PageSchemaProperties();
        page.setForm(form);
        page.setValidations(null);

        var props = new BffPagesProperties();
        props.setPages(Map.of("sample", page));

        assertThat(PageSchemaLinter.lint(props).hasErrors()).isFalse();
    }

    @Test
    void acceptsSupportedOperatorsKnownFieldsAndSupportedSeverity() {
        var field = new FieldProperties();
        field.setId("known");
        field.setValidations(List.of(rule("field-rule", "notBlank", List.of("known"), "required", "warning")));

        var section = new SectionProperties();
        section.setId("section");
        section.setFields(List.of(field));

        var form = new FormMetadataProperties();
        form.setSections(List.of(section));

        var page = new PageSchemaProperties();
        page.setForm(form);
        page.setValidations(List.of(rule("page-rule", "allGroupsEmpty", List.of("known"), "showMessage", "info")));

        var props = new BffPagesProperties();
        props.setPages(Map.of("sample", page));

        assertThat(PageSchemaLinter.lint(props).getErrors()).isEmpty();
    }

    @Test
    void reportsMissingIdUnsupportedOperatorsUnknownFieldsAndUnsupportedSeverity() {
        var known = new FieldProperties();
        known.setId("known");
        known.setValidations(List.of(rule(" ", "badWhen", List.of("known", "missing"), "badThen", "fatal")));

        var section = new SectionProperties();
        section.setId("section");
        section.setFields(List.of(known));

        var form = new FormMetadataProperties();
        form.setSections(List.of(section));

        var page = new PageSchemaProperties();
        page.setForm(form);

        var props = new BffPagesProperties();
        props.setPages(Map.of("sample", page));

        assertThat(PageSchemaLinter.lint(props).getErrors())
                .containsExactlyInAnyOrder(
                        "sample: validation rule missing id",
                        "sample: unsupported when.operator badWhen in rule  ",
                        "sample: rule   references unknown field missing",
                        "sample: unsupported then.operator badThen in rule  ",
                        "sample: unsupported severity fatal in rule  ");
    }

    @Test
    void nullWhenThenSeverityAndFieldsDoNotProduceErrors() {
        var field = new FieldProperties();
        field.setId("known");
        field.setValidations(List.of(rule("id", null, null, null, null)));

        var section = new SectionProperties();
        section.setId("section");
        section.setFields(List.of(field));

        var form = new FormMetadataProperties();
        form.setSections(List.of(section));

        var page = new PageSchemaProperties();
        page.setForm(form);

        var props = new BffPagesProperties();
        props.setPages(Map.of("sample", page));

        assertThat(PageSchemaLinter.lint(props).getErrors()).isEmpty();
    }

    private static ValidationRuleProperties rule(
            String id,
            String whenOperator,
            List<String> whenFields,
            String thenOperator,
            String severity) {
        var rule = new ValidationRuleProperties();
        rule.setId(id);
        if (whenOperator != null || whenFields != null) {
            var when = new RuleConditionProperties();
            when.setOperator(whenOperator);
            when.setFields(whenFields);
            rule.setWhen(when);
        }
        if (thenOperator != null) {
            var then = new RuleActionProperties();
            then.setOperator(thenOperator);
            rule.setThen(then);
        }
        rule.setSeverity(severity);
        return rule;
    }
}
