package com.bct.ngtpa.apiservice.adapter.in.web.validation;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.adapter.in.web.mapper.PageDisplayTextResolver;
import com.bct.ngtpa.apiservice.adapter.in.web.pageconfig.BffPagesProperties;
import com.bct.ngtpa.apiservice.adapter.in.web.pageconfig.FieldProperties;
import com.bct.ngtpa.apiservice.adapter.in.web.pageconfig.FormMetadataProperties;
import com.bct.ngtpa.apiservice.adapter.in.web.pageconfig.PageSchemaProperties;
import com.bct.ngtpa.apiservice.adapter.in.web.pageconfig.RuleActionProperties;
import com.bct.ngtpa.apiservice.adapter.in.web.pageconfig.RuleConditionProperties;
import com.bct.ngtpa.apiservice.adapter.in.web.pageconfig.SectionProperties;
import com.bct.ngtpa.apiservice.adapter.in.web.pageconfig.ValidationRuleProperties;
import com.bct.ngtpa.apiservice.adapter.in.web.request.UpdatePersonalInformationRequest;
import com.bct.ngtpa.apiservice.adapter.in.web.response.ApiError;
import com.bct.ngtpa.apiservice.shared.config.ConfigVariantCandidateGenerator;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Branch-oriented tests for {@link PersonalInformationUpdateYamlValidator}. Uses REAL {@link
 * ConfigVariantCandidateGenerator} and {@link PageDisplayTextResolver} instances (not mocked) -
 * confirmed (rounds 105/106) that {@code ConfigLookupContext}'s real 4-arg constructor exactly
 * matches the validator's own reflective {@code instantiateContext()} logic, so real
 * collaborators exercise the reflection call site naturally.
 */
class PersonalInformationUpdateYamlValidatorTest {

  private final ConfigVariantCandidateGenerator candidateGenerator =
      new ConfigVariantCandidateGenerator();
  private final PageDisplayTextResolver displayTextResolver =
      new PageDisplayTextResolver(candidateGenerator);

  private PersonalInformationUpdateYamlValidator newValidator(BffPagesProperties properties) {
    return new PersonalInformationUpdateYamlValidator(
        properties, displayTextResolver, candidateGenerator);
  }

  private PersonalInformationUpdateYamlValidator newValidatorNoDisplayResolver(
      BffPagesProperties properties) {
    return new PersonalInformationUpdateYamlValidator(properties, null, candidateGenerator);
  }

  // ----- fixture builders ---------------------------------------------------------

  private static ValidationRuleProperties rule(String id, String type, Object value) {
    var r = new ValidationRuleProperties();
    r.setId(id);
    r.setType(type);
    r.setValue(value);
    return r;
  }

  private static FieldProperties field(String id, ValidationRuleProperties... rules) {
    var f = new FieldProperties();
    f.setId(id);
    f.setValidations(rules.length == 0 ? null : Arrays.asList(rules));
    return f;
  }

  private static SectionProperties section(FieldProperties... fields) {
    var s = new SectionProperties();
    s.setId("section1");
    s.setFields(Arrays.asList(fields));
    return s;
  }

  private static PageSchemaProperties pageOf(SectionProperties... sections) {
    var page = new PageSchemaProperties();
    var form = new FormMetadataProperties();
    form.setSections(Arrays.asList(sections));
    page.setForm(form);
    return page;
  }

  private static BffPagesProperties propertiesOf(PageSchemaProperties page) {
    var properties = new BffPagesProperties();
    properties.setPages(Map.of("personalInformation", page));
    return properties;
  }

  private static UpdatePersonalInformationRequest requestOf(Map<String, Object> fields) {
    return new UpdatePersonalInformationRequest("1.0", false, fields);
  }

  // ----- page/request guards --------------------------------------------------

  @Test
  void missingPageConfigurationYieldsEmptyList() {
    BffPagesProperties emptyProperties = new BffPagesProperties();
    List<ApiError> errors =
        newValidator(emptyProperties).validate(requestOf(Map.of()), "en", "env1", "trust1", "SCHEME_A");
    assertThat(errors).isEmpty();
  }

  @Test
  void nullPagesPropertiesYieldsEmptyList() {
    List<ApiError> errors =
        newValidator(null).validate(requestOf(Map.of()), "en", "env1", "trust1", "SCHEME_A");
    assertThat(errors).isEmpty();
  }

  @Test
  void nullRequestYieldsEmptyList() {
    PageSchemaProperties page = pageOf(section(field("f1", rule("v1", "required", null))));
    List<ApiError> errors =
        newValidator(propertiesOf(page)).validate(null, "en", "env1", "trust1", "SCHEME_A");
    assertThat(errors).isEmpty();
  }

  @Test
  void nullRequestFieldsMapUsesEmptyDefaultAndStillEvaluatesRequired() {
    PageSchemaProperties page = pageOf(section(field("f1", rule("v1", "required", null))));
    UpdatePersonalInformationRequest request = new UpdatePersonalInformationRequest("1.0", false, null);
    List<ApiError> errors = newValidator(propertiesOf(page)).validate(request, "en", "env1", "trust1", "SCHEME_A");
    assertThat(errors).hasSize(1);
  }

  // ----- field skip conditions --------------------------------------------------

  @Test
  void fieldWithNullValidationsListIsSkipped() {
    var field = new FieldProperties();
    field.setId("f1");
    field.setValidations(null);
    PageSchemaProperties page = pageOf(section(field));
    List<ApiError> errors =
        newValidator(propertiesOf(page)).validate(requestOf(Map.of()), "en", "env1", "trust1", "SCHEME_A");
    assertThat(errors).isEmpty();
  }

  @Test
  void fieldWithBlankIdIsSkipped() {
    PageSchemaProperties page = pageOf(section(field("  ", rule("v1", "required", null))));
    List<ApiError> errors =
        newValidator(propertiesOf(page)).validate(requestOf(Map.of()), "en", "env1", "trust1", "SCHEME_A");
    assertThat(errors).isEmpty();
  }

  @Test
  void nonRequiredRuleSkippedWhenFieldNotSubmitted() {
    PageSchemaProperties page = pageOf(section(field("f1", rule("v1", "minLength", 5))));
    List<ApiError> errors =
        newValidator(propertiesOf(page)).validate(requestOf(Map.of()), "en", "env1", "trust1", "SCHEME_A");
    assertThat(errors).isEmpty();
  }

  @Test
  void nullRuleInValidationsListIsSkipped() {
    var field = new FieldProperties();
    field.setId("f1");
    field.setValidations(Arrays.asList((ValidationRuleProperties) null));
    PageSchemaProperties page = pageOf(section(field));
    List<ApiError> errors =
        newValidator(propertiesOf(page)).validate(requestOf(Map.of()), "en", "env1", "trust1", "SCHEME_A");
    assertThat(errors).isEmpty();
  }

  @Test
  void ruleWithBlankTypeIsSkipped() {
    PageSchemaProperties page = pageOf(section(field("f1", rule("v1", "  ", null))));
    List<ApiError> errors =
        newValidator(propertiesOf(page))
            .validate(requestOf(Map.of("f1", "value")), "en", "env1", "trust1", "SCHEME_A");
    assertThat(errors).isEmpty();
  }

  @Test
  void unknownRuleTypeNeverFails() {
    PageSchemaProperties page = pageOf(section(field("f1", rule("v1", "unknownType", null))));
    List<ApiError> errors =
        newValidator(propertiesOf(page))
            .validate(requestOf(Map.of("f1", "value")), "en", "env1", "trust1", "SCHEME_A");
    assertThat(errors).isEmpty();
  }

  // ----- field rules: required/minLength/maxLength/pattern/email/blockedAddress -----

  @Test
  void requiredRuleFailsWhenFieldMissingEntirely() {
    PageSchemaProperties page = pageOf(section(field("f1", rule("v1", "required", null))));
    List<ApiError> errors =
        newValidator(propertiesOf(page)).validate(requestOf(Map.of()), "en", "env1", "trust1", "SCHEME_A");
    assertThat(errors).hasSize(1);
    assertThat(errors.get(0).type()).isEqualTo("FIELD");
    assertThat(errors.get(0).targets()).containsExactly("f1");
  }

  @Test
  void requiredRuleSucceedsWhenFieldPresent() {
    PageSchemaProperties page = pageOf(section(field("f1", rule("v1", "required", null))));
    List<ApiError> errors =
        newValidator(propertiesOf(page))
            .validate(requestOf(Map.of("f1", "value")), "en", "env1", "trust1", "SCHEME_A");
    assertThat(errors).isEmpty();
  }

  @Test
  void minLengthRuleFailsWhenTooShort() {
    PageSchemaProperties page = pageOf(section(field("f1", rule("v1", "minLength", 5))));
    List<ApiError> errors =
        newValidator(propertiesOf(page))
            .validate(requestOf(Map.of("f1", "ab")), "en", "env1", "trust1", "SCHEME_A");
    assertThat(errors).hasSize(1);
  }

  @Test
  void maxLengthRuleFailsWhenTooLong() {
    PageSchemaProperties page = pageOf(section(field("f1", rule("v1", "maxLength", 3))));
    List<ApiError> errors =
        newValidator(propertiesOf(page))
            .validate(requestOf(Map.of("f1", "abcdef")), "en", "env1", "trust1", "SCHEME_A");
    assertThat(errors).hasSize(1);
  }

  @Test
  void patternRuleFailsWhenNotMatching() {
    PageSchemaProperties page = pageOf(section(field("f1", rule("v1", "pattern", "^[0-9]+$"))));
    List<ApiError> errors =
        newValidator(propertiesOf(page))
            .validate(requestOf(Map.of("f1", "abc")), "en", "env1", "trust1", "SCHEME_A");
    assertThat(errors).hasSize(1);
  }

  @Test
  void patternRuleSucceedsWhenMatching() {
    PageSchemaProperties page = pageOf(section(field("f1", rule("v1", "pattern", "^[0-9]+$"))));
    List<ApiError> errors =
        newValidator(propertiesOf(page))
            .validate(requestOf(Map.of("f1", "12345")), "en", "env1", "trust1", "SCHEME_A");
    assertThat(errors).isEmpty();
  }

  @Test
  void emailRuleFailsForInvalidEmail() {
    PageSchemaProperties page = pageOf(section(field("f1", rule("v1", "email", null))));
    List<ApiError> errors =
        newValidator(propertiesOf(page))
            .validate(requestOf(Map.of("f1", "not-an-email")), "en", "env1", "trust1", "SCHEME_A");
    assertThat(errors).hasSize(1);
  }

  @Test
  void emailRuleSucceedsForValidEmail() {
    PageSchemaProperties page = pageOf(section(field("f1", rule("v1", "email", null))));
    List<ApiError> errors =
        newValidator(propertiesOf(page))
            .validate(requestOf(Map.of("f1", "user@example.com")), "en", "env1", "trust1", "SCHEME_A");
    assertThat(errors).isEmpty();
  }

  @Test
  void blockedAddressRuleFailsWhenMatchingConfiguredPattern() {
    PageSchemaProperties page =
        pageOf(section(field("f1", rule("v1", "blockedAddress", "P\\.?O\\.?\\s*BOX"))));
    List<ApiError> errors =
        newValidator(propertiesOf(page))
            .validate(requestOf(Map.of("f1", "PO BOX 123")), "en", "env1", "trust1", "SCHEME_A");
    assertThat(errors).hasSize(1);
  }

  @Test
  void blockedAddressRuleSucceedsWhenNotMatching() {
    PageSchemaProperties page =
        pageOf(section(field("f1", rule("v1", "blockedAddress", "P\\.?O\\.?\\s*BOX"))));
    List<ApiError> errors =
        newValidator(propertiesOf(page))
            .validate(requestOf(Map.of("f1", "123 Main Street")), "en", "env1", "trust1", "SCHEME_A");
    assertThat(errors).isEmpty();
  }

  // ----- page rules: groupRequirement / conditionalRequired -------------------------

  @Test
  void groupRequirementRuleFiresWhenConditionMatches() {
    var condition = new RuleConditionProperties();
    condition.setOperator("notBlank");
    condition.setField("f1");
    var action = new RuleActionProperties();
    action.setTargets(List.of("f1", "f2"));
    var pageRule = new ValidationRuleProperties();
    pageRule.setId("pr1");
    pageRule.setType("groupRequirement");
    pageRule.setWhen(condition);
    pageRule.setThen(action);
    PageSchemaProperties page = pageOf(section(field("f1"), field("f2")));
    page.setValidations(List.of(pageRule));
    List<ApiError> errors =
        newValidator(propertiesOf(page))
            .validate(requestOf(Map.of("f1", "value")), "en", "env1", "trust1", "SCHEME_A");
    assertThat(errors).hasSize(1);
    assertThat(errors.get(0).type()).isEqualTo("CROSS_FIELD");
    assertThat(errors.get(0).targets()).containsExactly("f1", "f2");
  }

  @Test
  void conditionalRequiredRuleFiresPerBlankTarget() {
    var condition = new RuleConditionProperties();
    condition.setOperator("notBlank");
    condition.setField("f1");
    var action = new RuleActionProperties();
    action.setTargets(List.of("f2", "f3"));
    var pageRule = new ValidationRuleProperties();
    pageRule.setId("pr1");
    pageRule.setType("conditionalRequired");
    pageRule.setWhen(condition);
    pageRule.setThen(action);
    PageSchemaProperties page = pageOf(section(field("f1"), field("f2"), field("f3")));
    page.setValidations(List.of(pageRule));
    List<ApiError> errors =
        newValidator(propertiesOf(page))
            .validate(requestOf(Map.of("f1", "value", "f3", "present")), "en", "env1", "trust1", "SCHEME_A");
    assertThat(errors).hasSize(1);
    assertThat(errors.get(0).targets()).containsExactly("f2");
  }

  @Test
  void pageRuleWithNullTypeIsSkipped() {
    var pageRule = new ValidationRuleProperties();
    pageRule.setId("pr1");
    PageSchemaProperties page = pageOf(section(field("f1")));
    page.setValidations(Arrays.asList(pageRule, (ValidationRuleProperties) null));
    List<ApiError> errors =
        newValidator(propertiesOf(page)).validate(requestOf(Map.of()), "en", "env1", "trust1", "SCHEME_A");
    assertThat(errors).isEmpty();
  }

  // ----- condition operators ---------------------------------------------------------

  @Test
  void anyOperatorMatchesWhenAtLeastOneFieldNotBlank() {
    var condition = new RuleConditionProperties();
    condition.setOperator("any");
    condition.setFields(List.of("f1", "f2"));
    assertConditionMatches(condition, Map.of("f2", "value"));
  }

  @Test
  void allOperatorRequiresEveryFieldNotBlank() {
    var condition = new RuleConditionProperties();
    condition.setOperator("all");
    condition.setFields(List.of("f1", "f2"));
    assertConditionDoesNotMatch(condition, Map.of("f1", "value"));
    assertConditionMatches(condition, Map.of("f1", "a", "f2", "b"));
  }

  @Test
  void blankOperatorMatchesWhenAllFieldsBlank() {
    var condition = new RuleConditionProperties();
    condition.setOperator("blank");
    condition.setFields(List.of("f1", "f2"));
    assertConditionMatches(condition, Map.of());
    assertConditionDoesNotMatch(condition, Map.of("f1", "value"));
  }

  @Test
  void allGroupsEmptyOperatorMatchesWhenEveryGroupHasNoSubmittedFields() {
    var condition = new RuleConditionProperties();
    condition.setOperator("allGroupsEmpty");
    condition.setGroups(List.of(Map.of("fields", List.of("f1", "f2")), Map.of("fields", List.of("f3"))));
    assertConditionMatches(condition, Map.of());
    assertConditionDoesNotMatch(condition, Map.of("f1", "value"));
  }

  @Test
  void allGroupsEmptyWithNoConfiguredGroupsNeverMatches() {
    var condition = new RuleConditionProperties();
    condition.setOperator("allGroupsEmpty");
    assertConditionDoesNotMatch(condition, Map.of());
  }

  @Test
  void unknownOperatorNeverMatches() {
    var condition = new RuleConditionProperties();
    condition.setOperator("unknownOperator");
    condition.setFields(List.of("f1"));
    assertConditionDoesNotMatch(condition, Map.of("f1", "value"));
  }

  @Test
  void nullConditionNeverMatches() {
    var action = new RuleActionProperties();
    action.setTargets(List.of("f2"));
    var pageRule = new ValidationRuleProperties();
    pageRule.setType("groupRequirement");
    pageRule.setWhen(null);
    pageRule.setThen(action);
    PageSchemaProperties page = pageOf(section(field("f1"), field("f2")));
    page.setValidations(List.of(pageRule));
    List<ApiError> errors =
        newValidator(propertiesOf(page)).validate(requestOf(Map.of()), "en", "env1", "trust1", "SCHEME_A");
    assertThat(errors).isEmpty();
  }

  private void assertConditionMatches(RuleConditionProperties condition, Map<String, Object> submitted) {
    var action = new RuleActionProperties();
    action.setTargets(List.of("target1"));
    var pageRule = new ValidationRuleProperties();
    pageRule.setType("groupRequirement");
    pageRule.setWhen(condition);
    pageRule.setThen(action);
    PageSchemaProperties page = pageOf(section(field("f1"), field("f2"), field("f3")));
    page.setValidations(List.of(pageRule));
    List<ApiError> errors =
        newValidator(propertiesOf(page)).validate(requestOf(submitted), "en", "env1", "trust1", "SCHEME_A");
    assertThat(errors).hasSize(1);
  }

  private void assertConditionDoesNotMatch(RuleConditionProperties condition, Map<String, Object> submitted) {
    var action = new RuleActionProperties();
    action.setTargets(List.of("target1"));
    var pageRule = new ValidationRuleProperties();
    pageRule.setType("groupRequirement");
    pageRule.setWhen(condition);
    pageRule.setThen(action);
    PageSchemaProperties page = pageOf(section(field("f1"), field("f2"), field("f3")));
    page.setValidations(List.of(pageRule));
    List<ApiError> errors =
        newValidator(propertiesOf(page)).validate(requestOf(submitted), "en", "env1", "trust1", "SCHEME_A");
    assertThat(errors).isEmpty();
  }

  // ----- variant-based rule value resolution (REAL ConfigVariantCandidateGenerator) ----

  @Test
  void valueByVariantResolvesUsingRealCandidateGenerator() {
    var validationRule = rule("v1", "minLength", 2);
    validationRule.setValueByVariant(Map.of("env1.trust1.SCHEME_A", 10));
    PageSchemaProperties page = pageOf(section(field("f1", validationRule)));
    List<ApiError> errors =
        newValidator(propertiesOf(page))
            .validate(requestOf(Map.of("f1", "short")), "en", "env1", "trust1", "SCHEME_A");
    // "short" (5 chars) < variant-resolved minLength of 10 -> fails
    assertThat(errors).hasSize(1);
  }

  @Test
  void valueByVariantFallsBackToBaseValueWhenNoVariantMatches() {
    var validationRule = rule("v1", "minLength", 2);
    validationRule.setValueByVariant(Map.of("otherEnv.otherTrust.OTHER", 10));
    PageSchemaProperties page = pageOf(section(field("f1", validationRule)));
    List<ApiError> errors =
        newValidator(propertiesOf(page))
            .validate(requestOf(Map.of("f1", "ab")), "en", "env1", "trust1", "SCHEME_A");
    // no variant matches -> falls back to base value 2; "ab" length 2 >= 2 -> passes
    assertThat(errors).isEmpty();
  }

  // ----- message resolution ---------------------------------------------------------

  @Test
  void messageCodeResolvedViaRealPageDisplayTextResolver() {
    var validationRule = rule("v1", "required", null);
    validationRule.setMessageCode("myMessageCode");
    PageSchemaProperties page = pageOf(section(field("f1", validationRule)));
    page.setDisplay(Map.of("myMessageCode", Map.of("en", "Resolved via display config")));
    List<ApiError> errors =
        newValidator(propertiesOf(page)).validate(requestOf(Map.of()), "en", "env1", "trust1", "SCHEME_A");
    assertThat(errors).hasSize(1);
    assertThat(errors.get(0).message()).isEqualTo("Resolved via display config");
  }

  @Test
  void inlineMessageUsedWhenNoDisplayResolverConfigured() {
    var validationRule = rule("v1", "required", null);
    validationRule.setMessage(Map.of("en", "Inline fallback message"));
    PageSchemaProperties page = pageOf(section(field("f1", validationRule)));
    List<ApiError> errors =
        newValidatorNoDisplayResolver(propertiesOf(page))
            .validate(requestOf(Map.of()), "en", "env1", "trust1", "SCHEME_A");
    assertThat(errors).hasSize(1);
    assertThat(errors.get(0).message()).isEqualTo("Inline fallback message");
  }

  @Test
  void inlineMessageFallsBackToEnglishWhenLanguageKeyMissing() {
    var validationRule = rule("v1", "required", null);
    validationRule.setMessage(Map.of("en", "English message"));
    PageSchemaProperties page = pageOf(section(field("f1", validationRule)));
    List<ApiError> errors =
        newValidatorNoDisplayResolver(propertiesOf(page))
            .validate(requestOf(Map.of()), "fr", "env1", "trust1", "SCHEME_A");
    assertThat(errors).hasSize(1);
    assertThat(errors.get(0).message()).isEqualTo("English message");
  }

  @Test
  void messageDefaultsToCodeWhenNoMessageOrMessageCodeConfigured() {
    var validationRule = rule("v1", "required", null);
    validationRule.setCode("my.error.code");
    PageSchemaProperties page = pageOf(section(field("f1", validationRule)));
    List<ApiError> errors =
        newValidatorNoDisplayResolver(propertiesOf(page))
            .validate(requestOf(Map.of()), "en", "env1", "trust1", "SCHEME_A");
    assertThat(errors).hasSize(1);
    assertThat(errors.get(0).message()).isEqualTo("my.error.code");
    assertThat(errors.get(0).code()).isEqualTo("my.error.code");
  }

  // ----- deduplication ---------------------------------------------------------------

  @Test
  void identicalErrorsFromMultipleSourcesAreDeduplicated() {
    // Two fields, both named the same errorCode/message via identical rule
    // definitions, triggering effectively-duplicate ApiError instances.
    ValidationRuleProperties sharedRuleTemplate = rule("dup", "required", null);
    sharedRuleTemplate.setCode("shared.code");
    var field1 = field("f1", sharedRuleTemplate);
    // Re-use the exact SAME rule object reference on a differently-named field
    // still produces a DIFFERENT target list, so instead duplicate via the
    // SAME field id used twice at the page-rule level with identical targets.
    var condition = new RuleConditionProperties();
    condition.setOperator("blank");
    condition.setField("f2");
    var action = new RuleActionProperties();
    action.setTargets(List.of("f1"));
    var pageRule1 = new ValidationRuleProperties();
    pageRule1.setId("pr1");
    pageRule1.setType("conditionalRequired");
    pageRule1.setCode("dup.code");
    pageRule1.setWhen(condition);
    pageRule1.setThen(action);
    var pageRule2 = new ValidationRuleProperties();
    pageRule2.setId("pr1");
    pageRule2.setType("conditionalRequired");
    pageRule2.setCode("dup.code");
    pageRule2.setWhen(condition);
    pageRule2.setThen(action);
    PageSchemaProperties page = pageOf(section(field("f1"), field("f2")));
    page.setValidations(List.of(pageRule1, pageRule2));
    List<ApiError> errors =
        newValidator(propertiesOf(page)).validate(requestOf(Map.of()), "en", "env1", "trust1", "SCHEME_A");
    assertThat(errors).hasSize(1);
  }
}
