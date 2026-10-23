package com.bct.ngtpa.apiservice.adapter.in.web.validation;

import static org.assertj.core.api.Assertions.assertThat;

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

/**
 * Coverage closure for the PUT field-access validator: every defensive input of the page
 * configuration (null pages map, no form, null section, section without fields, null field, field
 * without a usable id, partial bindings) and the kinds of submitted value (non-text, empty and
 * non-empty collection). The validator must ignore what is malformed and keep checking what is not.
 */
class PersonalInformationFieldAccessValidatorDefensiveInputTest {

  private static final String NOT_EDITABLE = "personalInformation.field.notEditable";
  private static final String REQUIRED = "personalInformation.field.required";
  private static final String EMAIL_FIELD = "emailAddress";
  private static final String EMAIL_ITEM = "email";
  private static final String EMAIL_VALUE = "a@b.test";
  private static final String EDITABLE_COM = "EDITABLE_COM";
  private static final String EDITABLE_OPTION = "EDITABLE_OPTION";
  private static final String HIDDEN = "HIDDEN";

  // ----- page configuration that is missing or malformed ---------------------------------------

  @Test
  void aRequestWithoutAFieldsMapYieldsNoErrors() {
    var validator = validatorFor(pageWith(section(emailField())));

    assertThat(validator.validate(request(null), config(EMAIL_ITEM, HIDDEN))).isEmpty();
  }

  @Test
  void aRequestWithAnEmptyFieldsMapYieldsNoErrors() {
    var validator = validatorFor(pageWith(section(emailField())));

    assertThat(validator.validate(request(Map.of()), config(EMAIL_ITEM, HIDDEN))).isEmpty();
  }

  @Test
  void aNullPagesMapYieldsNoErrors() {
    var properties = new BffPagesProperties();
    properties.setPages(null);
    var validator = new PersonalInformationFieldAccessValidator(properties);

    assertThat(
            validator.validate(requestWith(EMAIL_FIELD, EMAIL_VALUE), config(EMAIL_ITEM, HIDDEN)))
        .isEmpty();
  }

  @Test
  void aPageWithoutAFormYieldsNoErrors() {
    var page = new PageSchemaProperties();
    page.setForm(null);
    var validator = validatorFor(page);

    assertThat(
            validator.validate(requestWith(EMAIL_FIELD, EMAIL_VALUE), config(EMAIL_ITEM, HIDDEN)))
        .isEmpty();
  }

  @Test
  void aFormWithoutSectionsYieldsNoErrors() {
    var form = new FormMetadataProperties();
    form.setSections(null);
    var page = new PageSchemaProperties();
    page.setForm(form);
    var validator = validatorFor(page);

    assertThat(
            validator.validate(requestWith(EMAIL_FIELD, EMAIL_VALUE), config(EMAIL_ITEM, HIDDEN)))
        .isEmpty();
  }

  @Test
  void aNullSectionIsSkippedAndTheNextSectionStillApplies() {
    var validator = validatorFor(pageWith(null, section(emailField())));

    assertThat(
            validator.validate(requestWith(EMAIL_FIELD, EMAIL_VALUE), config(EMAIL_ITEM, HIDDEN)))
        .containsExactly(error(NOT_EDITABLE));
  }

  @Test
  void aSectionWithoutFieldsIsSkippedAndTheNextSectionStillApplies() {
    var withoutFields = new SectionProperties();
    withoutFields.setId("empty");
    withoutFields.setFields(null);
    var validator = validatorFor(pageWith(withoutFields, section(emailField())));

    assertThat(
            validator.validate(requestWith(EMAIL_FIELD, EMAIL_VALUE), config(EMAIL_ITEM, HIDDEN)))
        .containsExactly(error(NOT_EDITABLE));
  }

  @Test
  void aNullFieldAndFieldsWithoutAUsableIdAreSkippedAndTheNextFieldStillApplies() {
    var withoutId = field(null, EMAIL_ITEM, EMAIL_ITEM);
    var withBlankId = field("  ", EMAIL_ITEM, EMAIL_ITEM);
    var validator = validatorFor(pageWith(section(null, withoutId, withBlankId, emailField())));

    assertThat(
            validator.validate(requestWith(EMAIL_FIELD, EMAIL_VALUE), config(EMAIL_ITEM, HIDDEN)))
        .containsExactly(error(NOT_EDITABLE));
  }

  @Test
  void aFieldWithABlankIdCannotBeAddressedByItsBlankName() {
    var withBlankId = field("  ", EMAIL_ITEM, EMAIL_ITEM);
    var validator = validatorFor(pageWith(section(withBlankId)));

    assertThat(validator.validate(requestWith("  ", EMAIL_VALUE), config(EMAIL_ITEM, HIDDEN)))
        .isEmpty();
  }

  // ----- bindings that do not protect anything ------------------------------------------------

  @Test
  void aBindingWithABlankDataKeyIsUiOnlyAndIgnored() {
    var validator = validatorFor(pageWith(section(field(EMAIL_FIELD, "", EMAIL_ITEM))));

    assertThat(
            validator.validate(requestWith(EMAIL_FIELD, EMAIL_VALUE), config(EMAIL_ITEM, HIDDEN)))
        .isEmpty();
  }

  @Test
  void aBindingWithoutAConfigKeyIsIgnored() {
    var validator = validatorFor(pageWith(section(field(EMAIL_FIELD, EMAIL_ITEM, null))));

    assertThat(
            validator.validate(requestWith(EMAIL_FIELD, EMAIL_VALUE), config(EMAIL_ITEM, HIDDEN)))
        .isEmpty();
  }

  @Test
  void aBindingWithABlankConfigKeyIsIgnored() {
    var validator = validatorFor(pageWith(section(field(EMAIL_FIELD, EMAIL_ITEM, "  "))));

    assertThat(
            validator.validate(requestWith(EMAIL_FIELD, EMAIL_VALUE), config(EMAIL_ITEM, HIDDEN)))
        .isEmpty();
  }

  // ----- the kinds of value a mandatory field may receive --------------------------------------

  @Test
  void aNonTextValueCountsAsPresentForAMandatoryField() {
    var validator = validatorFor(pageWith(section(emailField())));
    var config = config(EMAIL_ITEM, EDITABLE_COM);

    assertThat(validator.validate(requestWith(EMAIL_FIELD, Boolean.TRUE), config)).isEmpty();
    assertThat(validator.validate(requestWith(EMAIL_FIELD, 0), config)).isEmpty();
  }

  @Test
  void anEmptyCollectionCountsAsBlankForAMandatoryField() {
    var validator = validatorFor(pageWith(section(emailField())));

    assertThat(
            validator.validate(
                requestWith(EMAIL_FIELD, List.of()), config(EMAIL_ITEM, EDITABLE_COM)))
        .containsExactly(error(REQUIRED));
  }

  @Test
  void aNonEmptyCollectionCountsAsPresentForAMandatoryField() {
    var validator = validatorFor(pageWith(section(emailField())));

    assertThat(
            validator.validate(
                requestWith(EMAIL_FIELD, List.of(EMAIL_VALUE)), config(EMAIL_ITEM, EDITABLE_COM)))
        .isEmpty();
  }

  @Test
  void anEmptyCollectionIsAcceptedForAnOptionalField() {
    var validator = validatorFor(pageWith(section(emailField())));

    assertThat(
            validator.validate(
                requestWith(EMAIL_FIELD, List.of()), config(EMAIL_ITEM, EDITABLE_OPTION)))
        .isEmpty();
  }

  // ----- builders -------------------------------------------------------------------------------

  private static FieldProperties emailField() {
    return field(EMAIL_FIELD, EMAIL_ITEM, EMAIL_ITEM);
  }

  private static FieldProperties field(String id, String data, String config) {
    var field = new FieldProperties();
    field.setId(id);
    var binding = new ApimBindingProperties();
    binding.setData(data);
    binding.setConfig(config);
    field.setApimBinding(binding);
    return field;
  }

  private static SectionProperties section(FieldProperties... fields) {
    var section = new SectionProperties();
    section.setId("main");
    section.setFields(Arrays.asList(fields));
    return section;
  }

  private static PageSchemaProperties pageWith(SectionProperties... sections) {
    var form = new FormMetadataProperties();
    form.setSections(Arrays.asList(sections));
    var page = new PageSchemaProperties();
    page.setForm(form);
    return page;
  }

  private static PersonalInformationFieldAccessValidator validatorFor(PageSchemaProperties page) {
    var properties = new BffPagesProperties();
    properties.setPages(Map.of("personalInformation", page));
    return new PersonalInformationFieldAccessValidator(properties);
  }

  private static UpdatePersonalInformationRequest request(Map<String, Object> fields) {
    return new UpdatePersonalInformationRequest("1.0", false, fields);
  }

  private static UpdatePersonalInformationRequest requestWith(String key, Object value) {
    Map<String, Object> fields = new LinkedHashMap<>();
    fields.put(key, value);
    return request(fields);
  }

  private static Map<String, String> config(String itemId, String access) {
    return Map.of(itemId, access);
  }

  private static ApiError error(String code) {
    return ApiError.field(code, null, List.of(EMAIL_FIELD), "SERVER");
  }
}
