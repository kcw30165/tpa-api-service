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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PersonalInformationUpdateYamlValidatorAddressTest {

  private static final String AT_LEAST_ONE = "personalInformation.address.atLeastOneRequired";

  @Test
  void validatorUsesConfigVariantCandidateGenerator() throws Exception {
    String source =
        Files.readString(
            Path.of(
                "src/main/java/com/bct/ngtpa/apiservice/adapter/in/web/validation/PersonalInformationUpdateYamlValidator.java"));

    assertThat(source).contains("ConfigVariantCandidateGenerator");
    assertThat(source).contains("valueByVariant");
    assertThat(source).contains("messageCode");
  }

  @Test
  void bothLineOneKeysSubmittedBlankReturnsConfiguredCrossFieldError() {
    Map<String, Object> fields = new LinkedHashMap<>();
    fields.put("residentialAddressLine1", "");
    fields.put("mailingAddressLine1", "");
    fields.put("emailAddress", "member@example.test");

    assertThat(validate(fields))
        .anySatisfy(
            error -> {
              assertThat(error.type()).isEqualTo("CROSS_FIELD");
              assertThat(error.code()).isEqualTo(AT_LEAST_ONE);
              assertThat(error.targets())
                  .containsExactly("residentialAddressLine1", "mailingAddressLine1");
              assertThat(error.severity()).isEqualTo("ERROR");
              assertThat(error.source()).isEqualTo("SERVER");
            });
  }

  @Test
  void onlyEmailSubmittedDoesNotTriggerAddressRequirement() {
    assertThat(validate(Map.of("emailAddress", "member@example.test")))
        .as("D-28: Java cannot see the stored address when no line-1 key is submitted")
        .noneMatch(error -> AT_LEAST_ONE.equals(error.code()));
  }

  @Test
  void oneLineOneSubmittedBlankAndOtherAbsentDoesNotTriggerAddressRequirement() {
    Map<String, Object> fields = new LinkedHashMap<>();
    fields.put("residentialAddressLine1", "");
    fields.put("emailAddress", "member@example.test");

    assertThat(validate(fields))
        .as("D-25a/D-28: clearing one line 1 is allowed; the downstream checks the other")
        .noneMatch(error -> AT_LEAST_ONE.equals(error.code()));
  }

  @Test
  void allGroupsEmptyWithMalformedGroupFieldsIsFailSafe() {
    PageSchemaProperties page = properties().getPages().get("personalInformation");
    ValidationRuleProperties brokenGroupRule =
        page.getValidations().stream()
            .filter(rule -> "address.atLeastOneRequired".equals(rule.getId()))
            .findFirst()
            .orElseThrow();
    brokenGroupRule
        .getWhen()
        .setGroups(
            List.of(Map.of("name", "residentialAddress", "fields", "residentialAddressLine1")));

    assertThat(
            validator(propertiesOf(page))
                .validate(
                    new UpdatePersonalInformationRequest(
                        "1.0",
                        false,
                        Map.of(
                            "residentialAddressLine1", "Flat 15B, Block 2, Garden Villa Estate",
                            "residentialAddressLine2", "Tai Po, New Territories",
                            "residentialAddressLine3", "HK",
                            "residentialCountry", "HK")),
                    "en",
                    null,
                    null,
                    null))
        .isEmpty();
  }

  @Test
  void allGroupsEmptyWithoutConfiguredGroupsIsFailSafe() {
    PageSchemaProperties page = properties().getPages().get("personalInformation");
    ValidationRuleProperties brokenGroupRule =
        page.getValidations().stream()
            .filter(rule -> "address.atLeastOneRequired".equals(rule.getId()))
            .findFirst()
            .orElseThrow();
    brokenGroupRule.getWhen().setGroups(List.of());

    Map<String, Object> fields = new LinkedHashMap<>();
    fields.put("residentialAddressLine1", "");
    fields.put("mailingAddressLine1", "");

    assertThat(
            validator(propertiesOf(page))
                .validate(
                    new UpdatePersonalInformationRequest("1.0", false, fields),
                    "en",
                    null,
                    null,
                    null))
        .isEmpty();
  }

  @Test
  void mailingOnlyAddressPassesAddressRequirement() {
    assertThat(
            validate(
                Map.of(
                    "mailingAddressLine1", "12345 Mailing Road",
                    "mailingCountry", "HK",
                    "emailAddress", "member@example.test")))
        .noneMatch(error -> AT_LEAST_ONE.equals(error.code()))
        .noneMatch(error -> error.targets().contains("residentialAddressLine1"))
        .noneMatch(error -> error.targets().contains("residentialCountry"));
  }

  @Test
  void poBoxAddressReturnsConfiguredError() {
    assertThat(
            validate(
                Map.of(
                    "mailingAddressLine1", "PO Box 123",
                    "mailingCountry", "HK",
                    "emailAddress", "member@example.test")))
        .anySatisfy(
            error -> {
              assertThat(error.type()).isEqualTo("FIELD");
              assertThat(error.code()).isEqualTo("personalInformation.address.poBoxBlocked");
              assertThat(error.targets()).containsExactly("mailingAddressLine1");
            });
  }

  private List<ApiError> validate(Map<String, Object> fields) {
    return validator(properties())
        .validate(
            new UpdatePersonalInformationRequest("1.0", false, fields), "en", "JP", "JPM", "OE");
  }

  private PersonalInformationUpdateYamlValidator validator(BffPagesProperties properties) {
    return new PersonalInformationUpdateYamlValidator(
        properties,
        new PageDisplayTextResolver(new ConfigVariantCandidateGenerator()),
        new ConfigVariantCandidateGenerator());
  }

  private BffPagesProperties propertiesOf(PageSchemaProperties page) {
    BffPagesProperties properties = new BffPagesProperties();
    properties.setPages(Map.of("personalInformation", page));
    return properties;
  }

  private BffPagesProperties properties() {
    PageSchemaProperties page = new PageSchemaProperties();
    page.setDisplay(
        Map.of(
            "personalInformation.address.atLeastOneRequired.message",
            Map.of("en", "Please input a residential address or correspondence address."),
            "personalInformation.address.poBoxBlocked.message",
            Map.of("en", "PO Box address will not be accepted."),
            "personalInformation.residentialAddress.invalid.message",
            Map.of("en", "Please input a valid residential address."),
            "personalInformation.mailingAddress.invalid.message",
            Map.of("en", "Please input a valid correspondence address.")));
    page.setForm(
        form(
            List.of(
                field(
                    "residentialAddressLine1",
                    List.of(
                        minLength(
                            "residentialAddressLine1.minLength",
                            "personalInformation.residentialAddress.invalid",
                            "personalInformation.residentialAddress.invalid.message"),
                        blocked("residentialAddressLine1.poBoxBlocked"))),
                field(
                    "residentialAddressLine2",
                    List.of(blocked("residentialAddressLine2.poBoxBlocked"))),
                field(
                    "residentialAddressLine3",
                    List.of(blocked("residentialAddressLine3.poBoxBlocked"))),
                field("residentialCountry", List.of()),
                field(
                    "mailingAddressLine1",
                    List.of(
                        minLength(
                            "mailingAddressLine1.minLength",
                            "personalInformation.mailingAddress.invalid",
                            "personalInformation.mailingAddress.invalid.message"),
                        blocked("mailingAddressLine1.poBoxBlocked"))),
                field("mailingAddressLine2", List.of(blocked("mailingAddressLine2.poBoxBlocked"))),
                field("mailingAddressLine3", List.of(blocked("mailingAddressLine3.poBoxBlocked"))),
                field("mailingCountry", List.of()),
                field("emailAddress", List.of()))));
    page.setValidations(List.of(addressAtLeastOneRequired()));
    return propertiesOf(page);
  }

  private FormMetadataProperties form(List<FieldProperties> fields) {
    SectionProperties section = new SectionProperties();
    section.setId("addressInformation");
    section.setFields(fields);
    FormMetadataProperties form = new FormMetadataProperties();
    form.setSections(List.of(section));
    return form;
  }

  private FieldProperties field(String id, List<ValidationRuleProperties> validations) {
    FieldProperties field = new FieldProperties();
    field.setId(id);
    field.setValidations(validations);
    return field;
  }

  private ValidationRuleProperties minLength(String id, String code, String messageCode) {
    ValidationRuleProperties rule = new ValidationRuleProperties();
    rule.setId(id);
    rule.setType("minLength");
    rule.setValue(5);
    rule.setCode(code);
    rule.setMessageCode(messageCode);
    rule.setSeverity("error");
    return rule;
  }

  private ValidationRuleProperties blocked(String id) {
    ValidationRuleProperties rule = new ValidationRuleProperties();
    rule.setId(id);
    rule.setType("blockedAddress");
    rule.setValue("PO Box~|~P.O. Box~|~P O Box~|~P.O Box~|~Post Office Box~|~P.O. Box~|~郵政信箱");
    rule.setCode("personalInformation.address.poBoxBlocked");
    rule.setMessageCode("personalInformation.address.poBoxBlocked.message");
    rule.setSeverity("error");
    return rule;
  }

  private ValidationRuleProperties addressAtLeastOneRequired() {
    ValidationRuleProperties rule = new ValidationRuleProperties();
    rule.setId("address.atLeastOneRequired");
    rule.setType("groupRequirement");
    RuleConditionProperties when = new RuleConditionProperties();
    when.setOperator("allGroupsEmpty");
    when.setGroups(
        List.of(
            Map.of("name", "residentialAddress", "fields", List.of("residentialAddressLine1")),
            Map.of("name", "mailingAddress", "fields", List.of("mailingAddressLine1"))));
    RuleActionProperties then = new RuleActionProperties();
    then.setOperator("fail");
    then.setTargets(List.of("residentialAddressLine1", "mailingAddressLine1"));
    rule.setWhen(when);
    rule.setThen(then);
    rule.setCode(AT_LEAST_ONE);
    rule.setMessageCode("personalInformation.address.atLeastOneRequired.message");
    rule.setSeverity("error");
    return rule;
  }
}
