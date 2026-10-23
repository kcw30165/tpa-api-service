package com.bct.ngtpa.apiservice.adapter.in.web.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bct.ngtpa.apiservice.adapter.in.web.pageconfig.ApimBindingProperties;
import com.bct.ngtpa.apiservice.adapter.in.web.pageconfig.BffPagesProperties;
import com.bct.ngtpa.apiservice.adapter.in.web.pageconfig.FieldProperties;
import com.bct.ngtpa.apiservice.adapter.in.web.pageconfig.FormMetadataProperties;
import com.bct.ngtpa.apiservice.adapter.in.web.pageconfig.PageSchemaProperties;
import com.bct.ngtpa.apiservice.adapter.in.web.pageconfig.SectionProperties;
import com.bct.ngtpa.apiservice.adapter.in.web.request.UpdatePersonalInformationRequest;
import com.bct.ngtpa.apiservice.adapter.in.web.response.ApiError;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Sub-task 3.4a: the PUT field-access decision from the cached config (D-13, D-14, design A.3). */
class PersonalInformationFieldAccessValidatorTest {

  private static final String NOT_EDITABLE = "personalInformation.field.notEditable";
  private static final String REQUIRED = "personalInformation.field.required";

  private static FieldProperties field(String id, String configItemId, Boolean requiredOverride) {
    var field = new FieldProperties();
    field.setId(id);
    if (configItemId != null) {
      var binding = new ApimBindingProperties();
      binding.setData(configItemId);
      binding.setConfig(configItemId);
      field.setApimBinding(binding);
    }
    field.setRequiredOverride(requiredOverride);
    return field;
  }

  private static PersonalInformationFieldAccessValidator validator() {
    var section = new SectionProperties();
    section.setId("main");
    section.setFields(
        Arrays.asList(
            field("residentialAddressLine1", "addr1", Boolean.FALSE),
            field("residentialAddressLine2", "addr2", null),
            field("emailAddress", "email", null),
            field("smsLanguage", "sms-language", null),
            field("looseField", null, null)));
    var form = new FormMetadataProperties();
    form.setSections(List.of(section));
    var page = new PageSchemaProperties();
    page.setForm(form);
    var properties = new BffPagesProperties();
    properties.setPages(Map.of("personalInformation", page));
    return new PersonalInformationFieldAccessValidator(properties);
  }

  private static UpdatePersonalInformationRequest request(Object... keysAndValues) {
    Map<String, Object> fields = new LinkedHashMap<>();
    for (int i = 0; i < keysAndValues.length; i += 2) {
      fields.put((String) keysAndValues[i], keysAndValues[i + 1]);
    }
    return new UpdatePersonalInformationRequest("1.0", false, fields);
  }

  private static Map<String, String> config(String... itemIdsAndValues) {
    Map<String, String> config = new LinkedHashMap<>();
    for (int i = 0; i < itemIdsAndValues.length; i += 2) {
      config.put(itemIdsAndValues[i], itemIdsAndValues[i + 1]);
    }
    return config;
  }

  private static ApiError error(String code, String target) {
    return ApiError.field(code, null, List.of(target), "SERVER");
  }

  // ----- accepted ------------------------------------------------------------------------

  @Test
  void editableMandatoryFieldWithAValueIsAccepted() {
    assertThat(
            validator()
                .validate(request("emailAddress", "a@b.test"), config("email", "EDITABLE_COM")))
        .isEmpty();
  }

  @Test
  void editableOptionalFieldMayBeBlank() {
    assertThat(
            validator()
                .validate(request("smsLanguage", ""), config("sms-language", "EDITABLE_OPTION")))
        .isEmpty();
  }

  @Test
  void requiredOverrideFalseLowersEditableMandatoryToOptional() {
    assertThat(
            validator()
                .validate(request("residentialAddressLine1", ""), config("addr1", "EDITABLE_COM")))
        .isEmpty();
  }

  // ----- not editable --------------------------------------------------------------------

  @Test
  void hiddenFieldIsRejectedAsNotEditable() {
    assertThat(validator().validate(request("emailAddress", "a@b.test"), config("email", "HIDDEN")))
        .containsExactly(error(NOT_EDITABLE, "emailAddress"));
  }

  @Test
  void readonlyFieldIsRejectedAsNotEditable() {
    assertThat(
            validator().validate(request("emailAddress", "a@b.test"), config("email", "READONLY")))
        .containsExactly(error(NOT_EDITABLE, "emailAddress"));
  }

  @Test
  void blankConfigValueIsTreatedAsHiddenAndRejected() {
    assertThat(validator().validate(request("emailAddress", "a@b.test"), config("email", "")))
        .containsExactly(error(NOT_EDITABLE, "emailAddress"));
  }

  @Test
  void unrecognisedConfigValueIsTreatedAsHiddenAndRejected() {
    assertThat(validator().validate(request("emailAddress", "a@b.test"), config("email", "MAYBE")))
        .containsExactly(error(NOT_EDITABLE, "emailAddress"));
  }

  @Test
  void fieldAbsentFromTheConfigMapIsTreatedAsHiddenAndRejected() {
    assertThat(
            validator()
                .validate(request("emailAddress", "a@b.test"), config("addr1", "EDITABLE_COM")))
        .containsExactly(error(NOT_EDITABLE, "emailAddress"));
  }

  // ----- required ------------------------------------------------------------------------

  @Test
  void editableMandatoryBlankStringIsRejectedAsRequired() {
    assertThat(validator().validate(request("emailAddress", ""), config("email", "EDITABLE_COM")))
        .containsExactly(error(REQUIRED, "emailAddress"));
  }

  @Test
  void editableMandatoryNullValueIsRejectedAsRequired() {
    assertThat(validator().validate(request("emailAddress", null), config("email", "EDITABLE_COM")))
        .containsExactly(error(REQUIRED, "emailAddress"));
  }

  @Test
  void editableMandatoryWhitespaceValueIsRejectedAsRequired() {
    assertThat(
            validator().validate(request("emailAddress", "   "), config("email", "EDITABLE_COM")))
        .containsExactly(error(REQUIRED, "emailAddress"));
  }

  @Test
  void accessValuesAreCaseInsensitiveAndTrimmed() {
    assertThat(validator().validate(request("emailAddress", ""), config("email", " editable_com ")))
        .containsExactly(error(REQUIRED, "emailAddress"));
    assertThat(
            validator().validate(request("emailAddress", "a@b.test"), config("email", " hidden ")))
        .containsExactly(error(NOT_EDITABLE, "emailAddress"));
  }

  // ----- shape of the errors -------------------------------------------------------------

  @Test
  void errorCarriesTypeSeverityAndSourceAndNoMessage() {
    var errors =
        validator().validate(request("emailAddress", "a@b.test"), config("email", "READONLY"));

    assertThat(errors).hasSize(1);
    var error = errors.getFirst();
    assertThat(error.type()).isEqualTo("FIELD");
    assertThat(error.code()).isEqualTo(NOT_EDITABLE);
    assertThat(error.targets()).containsExactly("emailAddress");
    assertThat(error.severity()).isEqualTo("ERROR");
    assertThat(error.source()).isEqualTo("SERVER");
    assertThat(error.message()).isNull();
  }

  @Test
  void errorsFollowTheSubmissionOrder() {
    var errors =
        validator()
            .validate(
                request(
                    "smsLanguage",
                    "en",
                    "emailAddress",
                    "a@b.test",
                    "residentialAddressLine2",
                    "x"),
                config());

    assertThat(errors)
        .containsExactly(
            error(NOT_EDITABLE, "smsLanguage"),
            error(NOT_EDITABLE, "emailAddress"),
            error(NOT_EDITABLE, "residentialAddressLine2"));
  }

  @Test
  void mixedErrorsAreReportedTogether() {
    var errors =
        validator()
            .validate(
                request("emailAddress", "a@b.test", "residentialAddressLine2", ""),
                config("email", "HIDDEN", "addr2", "EDITABLE_COM"));

    assertThat(errors)
        .containsExactly(
            error(NOT_EDITABLE, "emailAddress"), error(REQUIRED, "residentialAddressLine2"));
  }

  // ----- ignored input and guards --------------------------------------------------------

  @Test
  void fieldWithoutAConfigBindingIsIgnored() {
    assertThat(validator().validate(request("looseField", "x"), config("email", "HIDDEN")))
        .isEmpty();
  }

  @Test
  void unknownSubmittedKeyIsIgnored() {
    assertThat(validator().validate(request("notInTheYaml", "x"), config("email", "HIDDEN")))
        .isEmpty();
  }

  @Test
  void onlySubmittedFieldsAreChecked() {
    assertThat(
            validator()
                .validate(
                    request("emailAddress", "a@b.test"),
                    config("email", "EDITABLE_COM", "sms-language", "HIDDEN", "addr1", "READONLY")))
        .isEmpty();
  }

  @Test
  void missingPageConfigurationYieldsNoErrors() {
    var validator = new PersonalInformationFieldAccessValidator(new BffPagesProperties());

    assertThat(validator.validate(request("emailAddress", "x"), config("email", "HIDDEN")))
        .isEmpty();
  }

  @Test
  void nullPagesPropertiesYieldsNoErrors() {
    var validator = new PersonalInformationFieldAccessValidator(null);

    assertThat(validator.validate(request("emailAddress", "x"), config("email", "HIDDEN")))
        .isEmpty();
  }

  @Test
  void nullRequestYieldsNoErrors() {
    assertThat(validator().validate(null, config("email", "HIDDEN"))).isEmpty();
  }

  @Test
  void nullConfigIsRejected() {
    var validator = validator();
    var request = request("emailAddress", "x");

    assertThatThrownBy(() -> validator.validate(request, null))
        .isInstanceOf(NullPointerException.class);
  }
}
