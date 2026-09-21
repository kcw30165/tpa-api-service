package com.bct.ngtpa.apiservice.adapter.in.web.pageconfig;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

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
    assertTrue(
        result.getErrors().isEmpty(), "Expected no lint errors but got: " + result.getErrors());
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
    field.setValidations(
        List.of(rule("field-rule", "notBlank", List.of("known"), "required", "warning")));
    var section = new SectionProperties();
    section.setId("section");
    section.setFields(List.of(field));
    var form = new FormMetadataProperties();
    form.setSections(List.of(section));
    var page = new PageSchemaProperties();
    page.setForm(form);
    page.setValidations(
        List.of(rule("page-rule", "allGroupsEmpty", List.of("known"), "showMessage", "info")));
    var props = new BffPagesProperties();
    props.setPages(Map.of("sample", page));
    assertThat(PageSchemaLinter.lint(props).getErrors()).isEmpty();
  }

  @Test
  void reportsMissingIdUnsupportedOperatorsUnknownFieldsAndUnsupportedSeverity() {
    var known = new FieldProperties();
    known.setId("known");
    known.setValidations(
        List.of(rule(" ", "badWhen", List.of("known", "missing"), "badThen", "fatal")));
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

  // ----- validatePageVariants() (added round 211 - ENTIRELY untested: no
  // existing test ever calls page.setVariants(...)) ------------------------------

  @Test
  void pageVariantWithBlankKeyReportsError() {
    var page = new PageSchemaProperties();
    var variant = new PageVariantProperties();
    variant.setTitle(Map.of("en", "Title"));
    variant.setBody(Map.of("en", "Body"));
    var action = new ActionProperties();
    action.setId("a1");
    action.setType("submit");
    action.setNextStep("next");
    action.setLabel(Map.of("en", "Submit"));
    variant.setActions(List.of(action));
    var variants = new LinkedHashMap<String, PageVariantProperties>();
    variants.put(" ", variant);
    page.setVariants(variants);
    var props = new BffPagesProperties();
    props.setPages(Map.of("sample", page));
    assertThat(PageSchemaLinter.lint(props).getErrors())
        .anyMatch(e -> e.contains("page variant has blank key"));
  }

  @Test
  void nullPageVariantReportsErrorAndSkipsFurtherChecks() {
    var page = new PageSchemaProperties();
    var variants = new LinkedHashMap<String, PageVariantProperties>();
    variants.put("v1", null);
    page.setVariants(variants);
    var props = new BffPagesProperties();
    props.setPages(Map.of("sample", page));
    assertThat(PageSchemaLinter.lint(props).getErrors())
        .containsExactly("sample: page variant v1 is null");
  }

  @Test
  void pageVariantMissingTitleAndBodyReportsErrors() {
    var page = new PageSchemaProperties();
    var variant = new PageVariantProperties();
    var action = new ActionProperties();
    action.setId("a1");
    action.setType("submit");
    action.setNextStep("next");
    action.setLabel(Map.of("en", "Submit"));
    variant.setActions(List.of(action));
    page.setVariants(Map.of("v1", variant));
    var props = new BffPagesProperties();
    props.setPages(Map.of("sample", page));
    assertThat(PageSchemaLinter.lint(props).getErrors())
        .containsExactlyInAnyOrder(
            "sample: page variant v1 requires title", "sample: page variant v1 requires body");
  }

  @Test
  void pageVariantWithNullOrEmptyActionsReportsErrorAndSkipsActionValidation() {
    var page = new PageSchemaProperties();
    var variant = new PageVariantProperties();
    variant.setTitle(Map.of("en", "Title"));
    variant.setBody(Map.of("en", "Body"));
    variant.setActions(List.of());
    page.setVariants(Map.of("v1", variant));
    var props = new BffPagesProperties();
    props.setPages(Map.of("sample", page));
    assertThat(PageSchemaLinter.lint(props).getErrors())
        .containsExactly("sample: page variant v1 requires actions");
  }

  @Test
  void pageVariantWithNullActionReportsError() {
    var page = new PageSchemaProperties();
    var variant = new PageVariantProperties();
    variant.setTitle(Map.of("en", "Title"));
    variant.setBody(Map.of("en", "Body"));
    variant.setActions(Arrays.asList((ActionProperties) null));
    page.setVariants(Map.of("v1", variant));
    var props = new BffPagesProperties();
    props.setPages(Map.of("sample", page));
    assertThat(PageSchemaLinter.lint(props).getErrors())
        .containsExactly("sample: page variant v1 contains null action");
  }

  @Test
  void pageVariantActionMissingIdNameTypeNextStepAndLabelReportsAllErrors() {
    var page = new PageSchemaProperties();
    var variant = new PageVariantProperties();
    variant.setTitle(Map.of("en", "Title"));
    variant.setBody(Map.of("en", "Body"));
    var action = new ActionProperties();
    variant.setActions(List.of(action));
    page.setVariants(Map.of("v1", variant));
    var props = new BffPagesProperties();
    props.setPages(Map.of("sample", page));
    assertThat(PageSchemaLinter.lint(props).getErrors())
        .containsExactlyInAnyOrder(
            "sample: page variant v1 action requires id or name",
            "sample: page variant v1 action requires type",
            "sample: page variant v1 action requires nextStep",
            "sample: page variant v1 action requires label");
  }

  @Test
  void pageVariantActionWithNameInsteadOfIdAndLabelCodeInsteadOfLabelSatisfiesRequirements() {
    var page = new PageSchemaProperties();
    var variant = new PageVariantProperties();
    variant.setTitle(Map.of("en", "Title"));
    variant.setBody(Map.of("en", "Body"));
    var action = new ActionProperties();
    action.setName("named-action");
    action.setType("submit");
    action.setNextStep("next");
    action.setLabelCode("action.label.code");
    variant.setActions(List.of(action));
    page.setVariants(Map.of("v1", variant));
    var props = new BffPagesProperties();
    props.setPages(Map.of("sample", page));
    assertThat(PageSchemaLinter.lint(props).getErrors()).isEmpty();
  }

  @Test
  void nullOrEmptyPageVariantsMapProducesNoErrors() {
    var pageWithNullVariants = new PageSchemaProperties();
    pageWithNullVariants.setVariants(null);
    var pageWithEmptyVariants = new PageSchemaProperties();
    pageWithEmptyVariants.setVariants(Map.of());
    var props = new BffPagesProperties();
    props.setPages(
        Map.of("nullVariants", pageWithNullVariants, "emptyVariants", pageWithEmptyVariants));
    assertThat(PageSchemaLinter.lint(props).hasErrors()).isFalse();
  }

  // ----- validateRuleValue() numeric/blocked-address checks (added round 211) --------

  @Test
  void blockedAddressRuleWithBlankValueReportsError() {
    var field = new FieldProperties();
    field.setId("known");
    var rule = ruleWithTypeAndValue("blockedAddress", null);
    field.setValidations(List.of(rule));
    var props = pageWithField(field);
    assertThat(PageSchemaLinter.lint(props).getErrors())
        .anyMatch(e -> e.contains("requires non-blank value"));
  }

  @Test
  void minLengthRuleWithNonNumericValueReportsError() {
    var field = new FieldProperties();
    field.setId("known");
    var rule = ruleWithTypeAndValue("minLength", "not-a-number");
    field.setValidations(List.of(rule));
    var props = pageWithField(field);
    assertThat(PageSchemaLinter.lint(props).getErrors())
        .anyMatch(e -> e.contains("requires numeric value"));
  }

  @Test
  void maxLengthRuleWithNumericStringValueDoesNotReportError() {
    var field = new FieldProperties();
    field.setId("known");
    var rule = ruleWithTypeAndValue("maxLength", "42");
    field.setValidations(List.of(rule));
    var props = pageWithField(field);
    assertThat(PageSchemaLinter.lint(props).getErrors()).isEmpty();
  }

  // ----- validateValueByVariant() checks (added round 211) --------------------------

  @Test
  void ruleWithBlankValueByVariantKeyReportsError() {
    var field = new FieldProperties();
    field.setId("known");
    var rule = ruleWithTypeAndValue("minLength", 5);
    rule.setValueByVariant(Map.of(" ", 10));
    field.setValidations(List.of(rule));
    var props = pageWithField(field);
    assertThat(PageSchemaLinter.lint(props).getErrors())
        .anyMatch(e -> e.contains("blank valueByVariant key"));
  }

  @Test
  void ruleWithNonNumericValueByVariantForMinLengthReportsError() {
    var field = new FieldProperties();
    field.setId("known");
    var rule = ruleWithTypeAndValue("minLength", 5);
    rule.setValueByVariant(Map.of("variant1", "not-a-number"));
    field.setValidations(List.of(rule));
    var props = pageWithField(field);
    assertThat(PageSchemaLinter.lint(props).getErrors())
        .anyMatch(e -> e.contains("requires numeric valueByVariant value for variant1"));
  }

  @Test
  void ruleWithNumericValueByVariantForMaxLengthProducesNoError() {
    var field = new FieldProperties();
    field.setId("known");
    var rule = ruleWithTypeAndValue("maxLength", 5);
    rule.setValueByVariant(Map.of("variant1", 20));
    field.setValidations(List.of(rule));
    var props = pageWithField(field);
    assertThat(PageSchemaLinter.lint(props).getErrors()).isEmpty();
  }

  private static ValidationRuleProperties ruleWithTypeAndValue(String type, Object value) {
    var rule = new ValidationRuleProperties();
    rule.setId("r1");
    rule.setType(type);
    rule.setValue(value);
    return rule;
  }

  private static BffPagesProperties pageWithField(FieldProperties field) {
    var section = new SectionProperties();
    section.setId("section");
    section.setFields(List.of(field));
    var form = new FormMetadataProperties();
    form.setSections(List.of(section));
    var page = new PageSchemaProperties();
    page.setForm(form);
    var props = new BffPagesProperties();
    props.setPages(Map.of("sample", page));
    return props;
  }
}
