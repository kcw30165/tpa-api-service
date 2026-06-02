package com.bct.ngtpa.apiservice.adapter.in.web.pageconfig;

import org.junit.jupiter.api.Test;

import java.util.List;

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
}
