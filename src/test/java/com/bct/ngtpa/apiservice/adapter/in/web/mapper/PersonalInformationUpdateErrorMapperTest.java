package com.bct.ngtpa.apiservice.adapter.in.web.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.adapter.in.web.pageconfig.ApimBindingProperties;
import com.bct.ngtpa.apiservice.adapter.in.web.pageconfig.BffPagesProperties;
import com.bct.ngtpa.apiservice.adapter.in.web.pageconfig.FieldProperties;
import com.bct.ngtpa.apiservice.adapter.in.web.pageconfig.FormMetadataProperties;
import com.bct.ngtpa.apiservice.adapter.in.web.pageconfig.PageSchemaProperties;
import com.bct.ngtpa.apiservice.adapter.in.web.pageconfig.SectionProperties;
import com.bct.ngtpa.apiservice.adapter.in.web.pageconfig.ValidationRuleProperties;
import com.bct.ngtpa.apiservice.adapter.in.web.response.ApiError;
import com.bct.ngtpa.apiservice.application.dto.UpdatePersonalInformationError;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PersonalInformationUpdateErrorMapperTest {

    @Test
    void mapsFieldErrorsUsingReverseYamlBindingAndValidationRules() {
        PersonalInformationUpdateErrorMapper mapper = new PersonalInformationUpdateErrorMapper(properties());

        List<ApiError> errors = mapper.toApiErrors(List.of(
                new UpdatePersonalInformationError("FIELD", List.of("email"), "REQUIRED"),
                new UpdatePersonalInformationError("FIELD", List.of("email"), "INVALID_FORMAT")));

        assertThat(errors).hasSize(2);
        assertThat(errors.get(0).type()).isEqualTo("FIELD");
        assertThat(errors.get(0).code()).isEqualTo("personalInformation.email.required");
        assertThat(errors.get(0).message()).isEqualTo("Please provide a valid email address.");
        assertThat(errors.get(0).targets()).containsExactly("emailAddress");
        assertThat(errors.get(1).code()).isEqualTo("personalInformation.email.invalid");
        assertThat(errors.get(1).targets()).containsExactly("emailAddress");
    }

    @Test
    void mapsCrossFieldTargetsUsingReverseYamlBindingWithoutHardcoding() {
        PersonalInformationUpdateErrorMapper mapper = new PersonalInformationUpdateErrorMapper(properties());

        List<ApiError> errors = mapper.toApiErrors(List.of(
                new UpdatePersonalInformationError("CROSS_FIELD", List.of("addr1", "addr2"), "AT_LEAST_ONE_REQUIRED")));

        assertThat(errors).hasSize(1);
        assertThat(errors.get(0).type()).isEqualTo("CROSS_FIELD");
        assertThat(errors.get(0).code()).isEqualTo("personalInformation.address.atLeastOneRequired");
        assertThat(errors.get(0).targets()).containsExactly("residentialAddressLine1", "residentialAddressLine2");
    }


    @Test
    void mapsApimValidationFailuresWithErrorSeverityAndServerSource() {
        PersonalInformationUpdateErrorMapper mapper = new PersonalInformationUpdateErrorMapper(properties());

        List<ApiError> errors = mapper.toApiErrors(List.of(
                new UpdatePersonalInformationError("FIELD", List.of("email"), "INVALID_FORMAT"),
                new UpdatePersonalInformationError(
                        "CROSS_FIELD",
                        List.of("addr1", "addr2"),
                        "AT_LEAST_ONE_REQUIRED")));

        assertThat(errors).hasSize(2);
        assertThat(errors.get(0).type()).isEqualTo("FIELD");
        assertThat(errors.get(0).severity()).isEqualTo("ERROR");
        assertThat(errors.get(0).source()).isEqualTo("SERVER");
        assertThat(errors.get(0).targets()).containsExactly("emailAddress");
        assertThat(errors.get(1).type()).isEqualTo("CROSS_FIELD");
        assertThat(errors.get(1).severity()).isEqualTo("ERROR");
        assertThat(errors.get(1).source()).isEqualTo("SERVER");
        assertThat(errors.get(1).targets()).containsExactly("residentialAddressLine1", "residentialAddressLine2");
    }

    private BffPagesProperties properties() {
        FieldProperties email = field("emailAddress", "email", validations(
                validation("required", "personalInformation.email.required", "Please provide a valid email address."),
                validation("email", "personalInformation.email.invalid", "Please provide a valid email address.")));
        FieldProperties addr1 = field("residentialAddressLine1", "addr1", List.of());
        FieldProperties addr2 = field("residentialAddressLine2", "addr2", List.of());

        SectionProperties section = new SectionProperties();
        section.setId("main");
        section.setFields(List.of(email, addr1, addr2));

        FormMetadataProperties form = new FormMetadataProperties();
        form.setSections(List.of(section));

        PageSchemaProperties page = new PageSchemaProperties();
        page.setForm(form);

        BffPagesProperties properties = new BffPagesProperties();
        properties.setPages(Map.of("personalInformation", page));
        return properties;
    }

    private FieldProperties field(String id, String apimName, List<ValidationRuleProperties> validations) {
        FieldProperties field = new FieldProperties();
        field.setId(id);
        ApimBindingProperties binding = new ApimBindingProperties();
        binding.setData(apimName);
        binding.setConfig(apimName);
        field.setApimBinding(binding);
        field.setValidations(validations);
        return field;
    }

    private List<ValidationRuleProperties> validations(ValidationRuleProperties... rules) {
        return List.of(rules);
    }

    private ValidationRuleProperties validation(String type, String code, String message) {
        ValidationRuleProperties rule = new ValidationRuleProperties();
        rule.setType(type);
        rule.setCode(code);
        rule.setMessage(Map.of("en", message));
        return rule;
    }
}
