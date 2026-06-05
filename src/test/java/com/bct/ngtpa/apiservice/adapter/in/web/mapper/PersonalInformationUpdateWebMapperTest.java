package com.bct.ngtpa.apiservice.adapter.in.web.mapper;

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
import com.bct.ngtpa.apiservice.application.exception.InvalidPersonalInformationUpdateException;
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
                () -> mapper.toCommand("ACC-123", true, new UpdatePersonalInformationRequest("1.0", true, Map.of("unknown", "x"))));
        assertEquals("Unknown personal information field: unknown", ex.getMessage());
    }

    @Test
    void omitsUiOnlyFieldWithOnlyConfigButRejectsWhenNothingRemains() {
        var ex = assertThrows(InvalidPersonalInformationUpdateException.class,
                () -> mapper.toCommand("ACC-123", true, new UpdatePersonalInformationRequest("1.0",true, 
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
}
