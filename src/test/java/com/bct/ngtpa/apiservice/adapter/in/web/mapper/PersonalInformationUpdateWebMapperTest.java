package com.bct.ngtpa.apiservice.adapter.in.web.mapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.bct.ngtpa.apiservice.adapter.in.web.pageconfig.ApimBindingProperties;
import com.bct.ngtpa.apiservice.adapter.in.web.pageconfig.BffPagesProperties;
import com.bct.ngtpa.apiservice.adapter.in.web.pageconfig.FieldProperties;
import com.bct.ngtpa.apiservice.adapter.in.web.pageconfig.FormMetadataProperties;
import com.bct.ngtpa.apiservice.adapter.in.web.pageconfig.PageSchemaProperties;
import com.bct.ngtpa.apiservice.adapter.in.web.pageconfig.SectionProperties;
import com.bct.ngtpa.apiservice.adapter.in.web.pageconfig.ValidationRuleProperties;
import com.bct.ngtpa.apiservice.adapter.in.web.request.UpdatePersonalInformationRequest;
import com.bct.ngtpa.apiservice.adapter.in.web.response.ApiError;
import com.bct.ngtpa.apiservice.application.dto.UpdatePersonalInformationError;
import com.bct.ngtpa.apiservice.application.exception.InvalidPersonalInformationUpdateException;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PersonalInformationUpdateWebMapperTest {

    private final PersonalInformationUpdateWebMapper mapper = new PersonalInformationUpdateWebMapper(properties());

    @Test
    void mapsBffFieldIdsToApimDataKeysUsingYamlBinding() {
        var fields = new LinkedHashMap<String, Object>();
        fields.put("residentialAddressLine2", "Tai Po, New Territories");
        fields.put("hongKongMobilePhone", "98765432");

        var command = mapper.toCommand("ACC-123", true, new UpdatePersonalInformationRequest("1.0", true, fields));

        assertEquals("ACC-123", command.accountRef());
        assertEquals(Map.of(
                "addr2", "Tai Po, New Territories",
                "mobile-number", "98765432"), command.updateFields());
    }

    @Test
    void rejectsUnknownFieldIds() {
        var ex = assertThrows(InvalidPersonalInformationUpdateException.class,
                () -> mapper.toCommand("ACC-123", true,
                        new UpdatePersonalInformationRequest("1.0", true, Map.of("unknown", "x"))));
        assertEquals("Unknown personal information field: unknown", ex.getMessage());
    }

    @Test
    void omitsUiOnlyFieldWithOnlyConfigButRejectsWhenNothingRemains() {
        var ex = assertThrows(InvalidPersonalInformationUpdateException.class,
                () -> mapper.toCommand("ACC-123", true, new UpdatePersonalInformationRequest("1.0", true,
                        Map.of("applyToAllMemberAccounts", true))));
        assertEquals("No updatable personal information fields were submitted.", ex.getMessage());
    }

    @Test
    void rejectsBlankRequiredSubmittedField() {
        var ex = assertThrows(InvalidPersonalInformationUpdateException.class,
                () -> mapper.toCommand("ACC-123", true, new UpdatePersonalInformationRequest("1.0", true,
                        Map.of("emailAddress", "   "))));
        assertEquals("Personal information field is required: emailAddress", ex.getMessage());
    }

    @Test
    void rejectsMaxLengthViolation() {
        var ex = assertThrows(InvalidPersonalInformationUpdateException.class,
                () -> mapper.toCommand("ACC-123", true, new UpdatePersonalInformationRequest("1.0", true,
                        Map.of("residentialAddressLine2", "x".repeat(41)))));
        assertEquals("Personal information field exceeds maxLength: residentialAddressLine2", ex.getMessage());
    }

    @Test
    void updateMapperRejectsMissingPageConfiguration() {
        PersonalInformationUpdateWebMapper mapper = new PersonalInformationUpdateWebMapper(
                new BffPagesProperties());

        assertThrows(IllegalStateException.class,
                () -> mapper.toCommand("ACC-123", false,
                        new UpdatePersonalInformationRequest("1.0", false,
                                Map.of("emailAddress", "a@b.com"))));
    }

    @Test
    void updateMapperSkipsNullSectionsNullFieldsAndBlankFieldIds() {
        FieldProperties blankId = field("   ", "blank-key", "string", "text", null, null, false);
        FieldProperties valid = field("middleName", "middle-name", null, "text", null, null, false);
        SectionProperties nullFieldSection = new SectionProperties();
        nullFieldSection.setFields(null);
        SectionProperties mixedSection = section(null, blankId, valid);
        PersonalInformationUpdateWebMapper mapper = new PersonalInformationUpdateWebMapper(
                properties(null, nullFieldSection, mixedSection));

        var command = mapper.toCommand("ACC-123", false, new UpdatePersonalInformationRequest("1.0", false,
                Map.of("middleName", "Nick")));

        assertThat(command.updateFields()).containsEntry("middle-name", "Nick");
    }

    @Test
    void updateMapperAcceptsBlankOptionalTextValueForUpdatableField() {
        PersonalInformationUpdateWebMapper mapper = new PersonalInformationUpdateWebMapper(
                properties(section(field(
                        "secondaryEmail", "secondary-email", "string", "email", null, null,
                        false))));

        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("secondaryEmail", "");

        var command = mapper.toCommand(
                "ACC-123",
                false,
                new UpdatePersonalInformationRequest("1.0", false, fields));

        assertThat(command.updateFields()).containsEntry("secondary-email", "");
    }

    @Test
    void updateMapperRejectsBooleanStringPatternAndEmailViolations() {
        PersonalInformationUpdateWebMapper mapper = new PersonalInformationUpdateWebMapper(properties(section(
                field("applyToAll", "apply-all", "boolean", "checkbox", null, null, false),
                field("displayName", "display-name", null, "text", null, null, false),
                field("homeTel", "home-phone", "string", "tel", null, "^[0-9]{8}$", false),
                field("emailAddress", "email", "string", "email", null, null, false))));

        assertEquals("Personal information field must be boolean: applyToAll",
                assertThrows(InvalidPersonalInformationUpdateException.class,
                        () -> mapper.toCommand("ACC-123", false,
                                new UpdatePersonalInformationRequest("1.0", false,
                                        Map.of("applyToAll", "true"))))
                        .getMessage());
        assertEquals("Personal information field must be string: displayName",
                assertThrows(InvalidPersonalInformationUpdateException.class,
                        () -> mapper.toCommand("ACC-123", false,
                                new UpdatePersonalInformationRequest("1.0", false,
                                        Map.of("displayName", 123))))
                        .getMessage());
        assertEquals("Personal information field pattern mismatch: homeTel",
                assertThrows(InvalidPersonalInformationUpdateException.class,
                        () -> mapper.toCommand("ACC-123", false,
                                new UpdatePersonalInformationRequest("1.0", false,
                                        Map.of("homeTel", "12AB"))))
                        .getMessage());
        assertEquals("Personal information field must be email: emailAddress",
                assertThrows(InvalidPersonalInformationUpdateException.class,
                        () -> mapper.toCommand("ACC-123", false,
                                new UpdatePersonalInformationRequest("1.0", false,
                                        Map.of("emailAddress",
                                                "not-an-email"))))
                        .getMessage());
    }

    @Test
    void errorMapperReturnsDefaultBusinessErrorWhenRawErrorsMissing() {
        PersonalInformationUpdateErrorMapper mapper = new PersonalInformationUpdateErrorMapper(
                properties(section()));

        assertThat(mapper.toApiErrors(null)).singleElement()
                .extracting(ApiError::type, ApiError::code, ApiError::message, ApiError::targets)
                .containsExactly("BUSINESS", "personalInformation.update.validationFailed",
                        "The submitted information is invalid.", List.of());
        assertThat(mapper.toApiErrors(List.of())).hasSize(1);
    }

    @Test
    void errorMapperNormalizesUnknownTypeDeduplicatesTargetsAndFallsBackWithoutConfig() {
        PersonalInformationUpdateErrorMapper mapper = new PersonalInformationUpdateErrorMapper(null);

        List<ApiError> errors = mapper.toApiErrors(Arrays.asList(
                null,
                UpdatePersonalInformationError.fromPipeSeparatedFields(
                        "strange",
                        " addr1 | addr1 | unknown | ",
                        null)));

        assertThat(errors).hasSize(1);
        assertThat(errors.get(0).type()).isEqualTo("FORM");
        assertThat(errors.get(0).code()).isEqualTo("personalInformation.update.validationFailed");
        assertThat(errors.get(0).message()).isEqualTo("The submitted information is invalid.");
        assertThat(errors.get(0).targets()).containsExactly("addr1", "unknown");
    }

    @Test
    void errorMapperUsesApimConfigBindingAndYamlRequiredValidation() {
        FieldProperties email = field("emailAddress", "email", "string", "email", null, null, false);
        email.setValidations(List
                .of(validation("required", "personalInformation.email.required", "Email is required")));
        PersonalInformationUpdateErrorMapper mapper = new PersonalInformationUpdateErrorMapper(
                properties(section(email)));

        ApiError error = mapper.toApiErrors(
                List.of(new UpdatePersonalInformationError("FIELD", List.of("email"), "REQUIRED")))
                .get(0);

        assertThat(error.type()).isEqualTo("FIELD");
        assertThat(error.targets()).containsExactly("emailAddress");
        assertThat(error.code()).isEqualTo("personalInformation.email.required");
        assertThat(error.message()).isEqualTo("Email is required");
    }

    @Test
    void errorMapperHandlesAtLeastOneRequiredAndInvalidFormatFallbacks() {
        PersonalInformationUpdateErrorMapper mapper = new PersonalInformationUpdateErrorMapper(
                properties(section(
                        field("homeTel", "home-phone", "string", "tel", null, null, false))));

        List<ApiError> errors = mapper.toApiErrors(List.of(
                new UpdatePersonalInformationError("CROSS_FIELD", List.of("home-phone"),
                        "AT_LEAST_ONE_REQUIRED"),
                new UpdatePersonalInformationError("FIELD", List.of("home-phone"), "INVALID_FORMAT"),
                new UpdatePersonalInformationError("FIELD", List.of(), "REQUIRED")));

        assertThat(errors.get(0).code()).isEqualTo("personalInformation.address.atLeastOneRequired");
        assertThat(errors.get(0).message())
                .isEqualTo("Please input a residential address or correspondence address.");
        assertThat(errors.get(1).targets()).containsExactly("homeTel");
        assertThat(errors.get(1).code()).isEqualTo("personalInformation.homeTel.invalid");
        assertThat(errors.get(1).message()).isEqualTo("Please input a valid value.");
        assertThat(errors.get(2).code()).isEqualTo("personalInformation.field.required");
        assertThat(errors.get(2).message()).isEqualTo("This field is required.");
    }

    @Test
    void updateMapperRejectsNullMissingAndEmptyRequests() {
        PersonalInformationUpdateWebMapper mapper = new PersonalInformationUpdateWebMapper(
                properties(section(field(
                        "emailAddress", "email", "email", "email", null, null, true))));

        assertThrows(InvalidPersonalInformationUpdateException.class,
                () -> mapper.toCommand("ACC-123", false, null));
        assertThrows(InvalidPersonalInformationUpdateException.class,
                () -> mapper.toCommand("ACC-123", false,
                        new UpdatePersonalInformationRequest("1.0", false, null)));
        assertThrows(InvalidPersonalInformationUpdateException.class,
                () -> mapper.toCommand("ACC-123", false,
                        new UpdatePersonalInformationRequest("1.0", false, Map.of())));
    }

    private static BffPagesProperties properties() {
        var bindingAddr2 = new ApimBindingProperties();
        bindingAddr2.setData("addr2");
        bindingAddr2.setConfig("addr2");
        var addr2 = new FieldProperties();
        addr2.setId("residentialAddressLine2");
        addr2.setDataType("string");
        addr2.setMaxLength(40);
        addr2.setApimBinding(bindingAddr2);

        var bindingMobile = new ApimBindingProperties();
        bindingMobile.setData("mobile-number");
        bindingMobile.setConfig("mobile-number");
        var mobile = new FieldProperties();
        mobile.setId("hongKongMobilePhone");
        mobile.setDataType("string");
        mobile.setMaxLength(8);
        mobile.setPattern("^[4-9][0-9]{7}$");
        mobile.setApimBinding(bindingMobile);

        var bindingEmail = new ApimBindingProperties();
        bindingEmail.setData("email");
        bindingEmail.setConfig("email");
        var email = new FieldProperties();
        email.setId("emailAddress");
        email.setDataType("string");
        email.setControlType("email");
        email.setMaxLength(120);

        var requiredValidation = new ValidationRuleProperties();
        requiredValidation.setType("required");
        email.setValidations(List.of(requiredValidation));

        email.setApimBinding(bindingEmail);

        var uiBinding = new ApimBindingProperties();
        uiBinding.setConfig("ui-check-box-apply-all-member");
        var applyAll = new FieldProperties();
        applyAll.setId("applyToAllMemberAccounts");
        applyAll.setDataType("boolean");
        applyAll.setApimBinding(uiBinding);

        var section = new SectionProperties();
        section.setId("section");
        section.setFields(List.of(addr2, mobile, email, applyAll));

        var form = new FormMetadataProperties();
        form.setSections(List.of(section));
        var page = new PageSchemaProperties();
        page.setForm(form);

        var props = new BffPagesProperties();
        props.setPages(Map.of("personalInformation", page));
        return props;
    }

    // Helpers
    private static BffPagesProperties properties(SectionProperties... sections) {
        BffPagesProperties properties = new BffPagesProperties();
        PageSchemaProperties page = new PageSchemaProperties();
        FormMetadataProperties form = new FormMetadataProperties();
        form.setSections(Arrays.asList(sections));
        page.setForm(form);
        properties.setPages(Map.of("personalInformation", page));
        return properties;
    }

    private static SectionProperties section(FieldProperties... fields) {
        SectionProperties section = new SectionProperties();
        section.setId("section");
        section.setFields(Arrays.asList(fields));
        return section;
    }

    private static FieldProperties field(String id, String dataKey, String dataType, String controlType,
            Integer maxLength, String pattern, boolean required) {
        FieldProperties field = new FieldProperties();
        field.setId(id);
        field.setDataType(dataType);
        field.setControlType(controlType);
        field.setMaxLength(maxLength);
        field.setPattern(pattern);
        ApimBindingProperties binding = new ApimBindingProperties();
        binding.setData(dataKey);
        binding.setConfig(dataKey);
        field.setApimBinding(binding);
        if (required) {
            field.setValidations(List.of(validation("required", "code.required", "Required")));
        }
        return field;
    }

    private static ValidationRuleProperties validation(String type, String code, String message) {
        ValidationRuleProperties rule = new ValidationRuleProperties();
        rule.setType(type);
        rule.setCode(code);
        rule.setMessage(Map.of("en", message));
        return rule;
    }
}
