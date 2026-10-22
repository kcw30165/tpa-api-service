package com.bct.ngtpa.apiservice.adapter.in.web.validation;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.adapter.in.web.mapper.PageDisplayTextResolver;
import com.bct.ngtpa.apiservice.adapter.in.web.pageconfig.BffPagesProperties;
import com.bct.ngtpa.apiservice.adapter.in.web.pageconfig.FieldGroupProperties;
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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the group presence rule (D-05) and the address country rule (D-08), using
 * synthetic fixtures with {@link PageSchemaProperties#getFieldGroups()} populated directly (not the
 * real bound YAML - see {@link PersonalInformationAddressGroupRealYamlRegressionTest} for the
 * end-to-end D-22 regression proof against real Spring Boot binding).
 */
class PersonalInformationFieldGroupPresenceTest {

  private static final String GROUP = "residentialAddress";
  private static final String MAILING_GROUP = "mailingAddress";
  private static final String LINE1 = "residentialAddressLine1";
  private static final String LINE2 = "residentialAddressLine2";
  private static final String LINE3 = "residentialAddressLine3";
  private static final String COUNTRY = "residentialCountry";
  private static final String MLINE1 = "mailingAddressLine1";
  private static final String MLINE2 = "mailingAddressLine2";
  private static final String MLINE3 = "mailingAddressLine3";
  private static final String MCOUNTRY = "mailingCountry";

  @Test
  void partialGroupSubmissionProducesIncompleteErrorWithMissingTargets() {
    List<ApiError> errors = validate(Map.of(LINE1, "Flat 1", "emailAddress", "a@b.test"));

    assertThat(errors)
        .anySatisfy(
            error -> {
              assertThat(error.code()).isEqualTo("personalInformation." + GROUP + ".incomplete");
              assertThat(error.targets()).containsExactlyInAnyOrder(LINE2, LINE3, COUNTRY);
            });
  }

  @Test
  void fullGroupSubmissionProducesNoIncompleteError() {
    List<ApiError> errors =
        validate(
            Map.of(
                LINE1, "Flat 1", LINE2, "", LINE3, "", COUNTRY, "HK", "emailAddress", "a@b.test"));

    assertThat(errors)
        .noneMatch(error -> error.code().equals("personalInformation." + GROUP + ".incomplete"));
  }

  @Test
  void absentGroupProducesNoIncompleteError() {
    List<ApiError> errors = validate(Map.of("emailAddress", "a@b.test"));

    assertThat(errors)
        .noneMatch(error -> error.code().equals("personalInformation." + GROUP + ".incomplete"));
  }

  @Test
  void countryNonBlankWithAllLinesBlankProducesIncompleteErrorTargetingFirstLine() {
    List<ApiError> errors =
        validate(
            Map.of(LINE1, "", LINE2, "", LINE3, "", COUNTRY, "HK", "emailAddress", "a@b.test"));

    assertThat(errors)
        .anySatisfy(
            error -> {
              assertThat(error.code()).isEqualTo("personalInformation." + GROUP + ".incomplete");
              assertThat(error.targets()).containsExactly(LINE1);
            });
  }

  @Test
  void countryNonBlankWithAnyLineNonBlankProducesNoCountryLineError() {
    List<ApiError> errors =
        validate(
            Map.of(
                LINE1, "Flat 1", LINE2, "", LINE3, "", COUNTRY, "HK", "emailAddress", "a@b.test"));

    assertThat(errors)
        .noneMatch(
            error ->
                error.code().equals("personalInformation." + GROUP + ".incomplete")
                    && error.targets().equals(List.of(LINE1)));
  }

  @Test
  void bothGroupsFullySubmittedAndBlankFiresAtLeastOneRequired() {
    Map<String, Object> fields = new LinkedHashMap<>();
    for (String f : List.of(LINE1, LINE2, LINE3, COUNTRY)) fields.put(f, "");
    for (String f : List.of(MLINE1, MLINE2, MLINE3, MCOUNTRY)) fields.put(f, "");
    fields.put("emailAddress", "a@b.test");

    List<ApiError> errors = validate(fields);

    assertThat(errors)
        .anyMatch(error -> "personalInformation.address.atLeastOneRequired".equals(error.code()));
  }

  @Test
  void oneGroupSubmittedBlankAndOtherGroupAbsentDoesNotFireAtLeastOneRequired() {
    Map<String, Object> fields = new LinkedHashMap<>();
    for (String f : List.of(LINE1, LINE2, LINE3, COUNTRY)) fields.put(f, "");
    fields.put("emailAddress", "a@b.test");

    List<ApiError> errors = validate(fields);

    assertThat(errors)
        .noneMatch(error -> "personalInformation.address.atLeastOneRequired".equals(error.code()));
  }

  @Test
  void neitherGroupSubmittedStillFiresAtLeastOneRequired() {
    List<ApiError> errors = validate(Map.of("emailAddress", "a@b.test"));

    assertThat(errors)
        .anyMatch(error -> "personalInformation.address.atLeastOneRequired".equals(error.code()));
  }

  @Test
  void genericGroupPresenceAppliesToNonAddressGroupsToo() {
    PageSchemaProperties page = new PageSchemaProperties();
    page.setForm(form(field("hongKongBusinessPhone"), field("hongKongBusinessPhoneExtension")));
    page.setFieldGroups(
        List.of(group("businessPhone", "hongKongBusinessPhone", "hongKongBusinessPhoneExtension")));
    BffPagesProperties properties = new BffPagesProperties();
    properties.setPages(Map.of("personalInformation", page));
    PersonalInformationUpdateYamlValidator validator =
        new PersonalInformationUpdateYamlValidator(
            properties,
            new PageDisplayTextResolver(new ConfigVariantCandidateGenerator()),
            new ConfigVariantCandidateGenerator());

    List<ApiError> errors =
        validator.validate(
            new UpdatePersonalInformationRequest(
                "1.0", false, Map.of("hongKongBusinessPhone", "21234567")),
            "en",
            "JP",
            "JPM",
            "OE");

    assertThat(errors)
        .anySatisfy(
            error -> {
              assertThat(error.code()).isEqualTo("personalInformation.businessPhone.incomplete");
              assertThat(error.targets()).containsExactly("hongKongBusinessPhoneExtension");
            });
  }

  private List<ApiError> validate(Map<String, Object> fields) {
    PersonalInformationUpdateYamlValidator validator =
        new PersonalInformationUpdateYamlValidator(
            properties(),
            new PageDisplayTextResolver(new ConfigVariantCandidateGenerator()),
            new ConfigVariantCandidateGenerator());
    return validator.validate(
        new UpdatePersonalInformationRequest("1.0", false, fields), "en", "JP", "JPM", "OE");
  }

  private BffPagesProperties properties() {
    PageSchemaProperties page = new PageSchemaProperties();
    page.setForm(
        form(
            field(LINE1),
            field(LINE2),
            field(LINE3),
            field(COUNTRY),
            field(MLINE1),
            field(MLINE2),
            field(MLINE3),
            field(MCOUNTRY),
            field("emailAddress")));
    page.setFieldGroups(
        List.of(
            group(GROUP, LINE1, LINE2, LINE3, COUNTRY),
            group(MAILING_GROUP, MLINE1, MLINE2, MLINE3, MCOUNTRY)));
    page.setValidations(List.of(addressAtLeastOneRequired()));
    BffPagesProperties properties = new BffPagesProperties();
    properties.setPages(Map.of("personalInformation", page));
    return properties;
  }

  private FormMetadataProperties form(FieldProperties... fields) {
    SectionProperties section = new SectionProperties();
    section.setId("s1");
    section.setFields(List.of(fields));
    FormMetadataProperties form = new FormMetadataProperties();
    form.setSections(List.of(section));
    return form;
  }

  private FieldProperties field(String id) {
    FieldProperties field = new FieldProperties();
    field.setId(id);
    return field;
  }

  private FieldGroupProperties group(String name, String... fields) {
    FieldGroupProperties group = new FieldGroupProperties();
    group.setName(name);
    group.setFields(new ArrayList<>(List.of(fields)));
    return group;
  }

  private ValidationRuleProperties addressAtLeastOneRequired() {
    ValidationRuleProperties rule = new ValidationRuleProperties();
    rule.setId("address.atLeastOneRequired");
    rule.setType("groupRequirement");
    RuleConditionProperties when = new RuleConditionProperties();
    when.setOperator("allGroupsEmpty");
    when.setGroups(
        List.of(
            Map.of("name", GROUP, "fields", List.of(LINE1, LINE2, LINE3, COUNTRY)),
            Map.of("name", MAILING_GROUP, "fields", List.of(MLINE1, MLINE2, MLINE3, MCOUNTRY))));
    RuleActionProperties then = new RuleActionProperties();
    then.setOperator("fail");
    then.setTargets(List.of(LINE1, MLINE1));
    rule.setWhen(when);
    rule.setThen(then);
    rule.setCode("personalInformation.address.atLeastOneRequired");
    rule.setSeverity("error");
    return rule;
  }
}
