package com.bct.ngtpa.apiservice.adapter.in.web.pageconfig;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

class PersonalInformationPhoneEmailValidationYamlTest {

  @Test
  void hongKongPhoneRulesAreConfiguredFromYaml() {
    assertPatternRule(
        "hongKongBusinessPhone",
        "hongKongBusinessPhone.integer",
        "^[0-9]*$",
        "personalInformation.businessPhone.invalid",
        "personalInformation.businessPhone.invalid.message");
    assertPatternRule(
        "hongKongBusinessPhoneExtension",
        "hongKongBusinessPhoneExtension.integer",
        "^[0-9]*$",
        "personalInformation.businessPhoneExtension.invalid",
        "personalInformation.businessPhoneExtension.invalid.message");
    assertPatternRule(
        "hongKongMobilePhone",
        "hongKongMobilePhone.hkMobile",
        "^[4-9][0-9]*$",
        "personalInformation.mobilePhone.invalid",
        "personalInformation.mobilePhone.invalid.message");

    Map<String, Object> rule = pageRule("businessPhone.requiredWhenExtensionPresent");
    assertThat(string(when(rule).get("operator"))).isEqualTo("notBlank");
    assertThat(fields(when(rule))).containsExactly("hongKongBusinessPhoneExtension");
    assertThat(string(then(rule).get("operator"))).isEqualTo("required");
    assertThat(targets(then(rule))).containsExactly("hongKongBusinessPhone");
    assertThat(rule.get("code"))
        .isEqualTo("personalInformation.businessPhone.requiredWhenExtensionPresent");
    assertThat(rule.get("messageCode"))
        .isEqualTo("personalInformation.businessPhone.requiredWhenExtensionPresent.message");
  }

  @Test
  void overseasPhoneDependencyRulesAreConfiguredWithoutMixingAddressCountries() {
    assertPatternRule(
        "overseasCountryCode",
        "overseasCountryCode.integer",
        "^[0-9]*$",
        "personalInformation.overseasCountryCode.invalid",
        "personalInformation.overseasCountryCode.invalid.message");
    assertPatternRule(
        "overseasAreaCode",
        "overseasAreaCode.integer",
        "^[0-9]*$",
        "personalInformation.overseasAreaCode.invalid",
        "personalInformation.overseasAreaCode.invalid.message");
    assertPatternRule(
        "overseasPhoneNumber",
        "overseasPhoneNumber.integer",
        "^[0-9]*$",
        "personalInformation.overseasPhoneNumber.invalid",
        "personalInformation.overseasPhoneNumber.invalid.message");

    Map<String, Object> phoneRequiredRule = pageRule("overseasPhoneNumber.requiredWhenCodePresent");
    assertThat(string(when(phoneRequiredRule).get("operator"))).isEqualTo("any");
    assertThat(fields(when(phoneRequiredRule)))
        .containsExactly("overseasCountryCode", "overseasAreaCode");
    assertThat(targets(then(phoneRequiredRule))).containsExactly("overseasPhoneNumber");
    assertThat(phoneRequiredRule.get("code"))
        .isEqualTo("personalInformation.overseasPhoneNumber.requiredWhenCodePresent");

    Map<String, Object> countryRequiredRule =
        pageRule("overseasCountryCode.requiredWhenOverseasPhonePresent");
    assertThat(string(when(countryRequiredRule).get("operator"))).isEqualTo("notBlank");
    assertThat(fields(when(countryRequiredRule))).containsExactly("overseasPhoneNumber");
    assertThat(targets(then(countryRequiredRule))).containsExactly("overseasCountryCode");

    assertThat(targets(then(countryRequiredRule)))
        .doesNotContain("residentialCountry", "mailingCountry");
    assertThat(fields(when(countryRequiredRule)))
        .doesNotContain("residentialAddressLine1", "mailingAddressLine1");
  }

  @Test
  void faxRuleAllowsOnlyConfiguredPermittedCharactersPattern() {
    assertPatternRule(
        "faxNo",
        "faxNo.permittedCharacters",
        "^[0-9 \\-]*$",
        "personalInformation.faxNo.invalid",
        "personalInformation.faxNo.invalid.message");
  }

  @Test
  void emailRulesAreMultipleIndependentRules() {
    List<Map<String, Object>> rules = validations(field("emailAddress"));

    Map<String, Object> required = rule(rules, "email.required");
    assertThat(required.get("type")).isEqualTo("required");
    assertThat(required.get("code")).isEqualTo("personalInformation.email.required");
    assertThat(required.get("messageCode")).isEqualTo("personalInformation.email.required.message");

    Map<String, Object> email = rule(rules, "email.invalid");
    assertThat(email.get("type")).isEqualTo("pattern");
    assertThat(email.get("value")).isEqualTo("^[a-zA-Z0-9._-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$");
    assertThat(email.get("code")).isEqualTo("personalInformation.email.invalid");
    assertThat(email.get("messageCode")).isEqualTo("personalInformation.email.invalid.message");

    assertThat(rules)
        .as("D-16/D4: the email.minLength rule must be removed")
        .noneMatch(rule -> "email.minLength".equals(rule.get("id")));
  }

  private void assertPatternRule(
      String fieldId,
      String ruleId,
      String expectedPattern,
      String expectedCode,
      String expectedMessageCode) {
    Map<String, Object> rule = rule(validations(field(fieldId)), ruleId);
    assertThat(rule.get("type")).isEqualTo("pattern");
    assertThat(rule.get("value")).isEqualTo(expectedPattern);
    assertThat(rule.get("code")).isEqualTo(expectedCode);
    assertThat(rule.get("messageCode")).isEqualTo(expectedMessageCode);
    assertThat(rule.get("severity")).isEqualTo("error");
  }

  private Map<String, Object> pageRule(String ruleId) {
    return rule(validations(personalInformationPage()), ruleId);
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> personalInformationPage() {
    try (InputStream inputStream =
        getClass()
            .getClassLoader()
            .getResourceAsStream("application-page-personal-information.yml")) {
      assertThat(inputStream)
          .as("application-page-personal-information.yml must be on test classpath")
          .isNotNull();
      Map<String, Object> root = new Yaml().load(inputStream);
      return (Map<String, Object>)
          ((Map<String, Object>) ((Map<String, Object>) root.get("bff-pages")).get("pages"))
              .get("personalInformation");
    } catch (Exception exception) {
      throw new AssertionError("Failed to load personal-information YAML", exception);
    }
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> display() {
    return (Map<String, Object>) personalInformationPage().get("display");
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
  private List<String> targets(Map<String, Object> node) {
    Object value = node.get("targets");
    return value instanceof List<?> list ? (List<String>) list : List.of();
  }

  private String string(Object value) {
    return value == null ? "" : value.toString();
  }
}
