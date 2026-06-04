package com.bct.ngtpa.apiservice.adapter.in.web.mapper;

import com.bct.ngtpa.apiservice.adapter.in.web.pageconfig.ApimBindingProperties;
import com.bct.ngtpa.apiservice.adapter.in.web.pageconfig.BffPagesProperties;
import com.bct.ngtpa.apiservice.adapter.in.web.pageconfig.FieldProperties;
import com.bct.ngtpa.apiservice.adapter.in.web.pageconfig.FormMetadataProperties;
import com.bct.ngtpa.apiservice.adapter.in.web.pageconfig.PageMetadataProperties;
import com.bct.ngtpa.apiservice.adapter.in.web.pageconfig.PageSchemaProperties;
import com.bct.ngtpa.apiservice.adapter.in.web.pageconfig.SectionProperties;
import com.bct.ngtpa.apiservice.application.dto.MemberInfoConfigItem;
import com.bct.ngtpa.apiservice.application.dto.MemberInfoConfigItemType;
import com.bct.ngtpa.apiservice.application.dto.PersonalInformationResult;
import com.bct.ngtpa.apiservice.infrastructure.logging.LoggingSanitizer;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class PersonalInformationWebMapperTest {

    @Test
    void mapsDataConfigItemsToBffFieldsUsingApimDataItemId() {
        PersonalInformationWebMapper mapper = mapper(mock(LoggingSanitizer.class));
        var result = new PersonalInformationResult(
                Map.of("addr1", "ABC Street", "email", "nick@example.com"),
                Map.of("addr1", "EDITABLE_COM", "email", "READONLY"),
                Map.of(
                        "addr1", item("addr1", MemberInfoConfigItemType.DATA, "EDITABLE_COM"),
                        "email", item("email", MemberInfoConfigItemType.DATA, "READONLY")));

        Map<String, Object> response = mapper.toResponse(result, "en");

        List<?> fields = firstSectionFields(response);
        Map<String, Object> addressField = asMap(fields.get(0));
        Map<String, Object> emailField = asMap(fields.get(1));

        assertThat(addressField.get("name")).isEqualTo("residentialAddressLine1");
        assertThat(addressField.get("value")).isEqualTo("ABC Street");
        assertThat(addressField.get("readonly")).isEqualTo(false);
        assertThat(addressField.get("required")).isEqualTo(true);

        assertThat(emailField.get("name")).isEqualTo("emailAddress");
        assertThat(emailField.get("value")).isEqualTo("nick@example.com");
        assertThat(emailField.get("readonly")).isEqualTo(true);
        assertThat(emailField.get("required")).isEqualTo(false);
    }

    @Test
    void uiConfigItemAppliesConfigValueButDoesNotPopulateValueFromApimData() {
        PersonalInformationWebMapper mapper = mapper(mock(LoggingSanitizer.class));
        var result = new PersonalInformationResult(
                Map.of("ui-check-box-apply-all-member", true),
                Map.of("ui-check-box-apply-all-member", "READONLY"),
                Map.of("ui-check-box-apply-all-member",
                        item("ui-check-box-apply-all-member", MemberInfoConfigItemType.UI, "READONLY")));

        Map<String, Object> response = mapper.toResponse(result, "en");

        List<?> fields = firstSectionFields(response);
        Map<String, Object> uiField = asMap(fields.get(0));
        assertThat(uiField.get("name")).isEqualTo("applyToAllMemberAccounts");
        assertThat(uiField.get("value")).isNull();
        assertThat(uiField.get("readonly")).isEqualTo(true);
    }

    @Test
    void hiddenConfigItemIsOmittedFromFrontendResponse() {
        PersonalInformationWebMapper mapper = mapper(mock(LoggingSanitizer.class));
        var result = new PersonalInformationResult(
                Map.of("addr1", "ABC Street"),
                Map.of("addr1", "HIDDEN"),
                Map.of("addr1", item("addr1", MemberInfoConfigItemType.DATA, "HIDDEN")));

        Map<String, Object> response = mapper.toResponse(result, "en");

        assertThat(sections(response)).isEmpty();
    }

    @Test
    void sectionWithNoReturnedFieldsIsOmittedFromFrontendResponse() {
        PersonalInformationWebMapper mapper = mapper(mock(LoggingSanitizer.class));
        var result = new PersonalInformationResult(
                Map.of("addr1", "ABC Street"),
                Map.of("addr1", "HIDDEN"),
                Map.of("addr1", item("addr1", MemberInfoConfigItemType.DATA, "HIDDEN")));

        Map<String, Object> response = mapper.toResponse(result, "en");

        assertThat(sections(response)).isEmpty();
    }

    @Test
    void ruleConfigItemIsIgnoredForNow() {
        PersonalInformationWebMapper mapper = mapper(mock(LoggingSanitizer.class));
        var result = new PersonalInformationResult(
                Map.of("addr1", "ABC Street"),
                Map.of("addr1", "EDITABLE_COM"),
                Map.of("addr1", item("addr1", MemberInfoConfigItemType.RULE, "EDITABLE_COM")));

        Map<String, Object> response = mapper.toResponse(result, "en");

        assertThat(sections(response)).isEmpty();
    }

    @Test
    void unknownConfigItemTypeIsIgnoredAndLoggedAsError() {
        LoggingSanitizer sanitizer = mock(LoggingSanitizer.class);
        PersonalInformationWebMapper mapper = mapper(sanitizer);
        var result = new PersonalInformationResult(
                Map.of("addr1", "ABC Street"),
                Map.of("addr1", "EDITABLE_COM"),
                Map.of("addr1", item("addr1", MemberInfoConfigItemType.UNKNOWN, "EDITABLE_COM")));

        Map<String, Object> response = mapper.toResponse(result, "en");

        assertThat(sections(response)).isEmpty();
        verify(sanitizer, atLeastOnce()).toSafeString(org.mockito.ArgumentMatchers.any());
    }

    private PersonalInformationWebMapper mapper(LoggingSanitizer sanitizer) {
        return new PersonalInformationWebMapper(sanitizer, pageConfig());
    }

    private MemberInfoConfigItem item(String itemId, MemberInfoConfigItemType itemType, String configValue) {
        return MemberInfoConfigItem.of(itemId, itemType, configValue);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> asMap(Object value) {
        return (Map<String, Object>) value;
    }

    private List<?> firstSectionFields(Map<String, Object> response) {
        List<?> sections = sections(response);
        Map<String, Object> addressSection = asMap(sections.get(0));
        return (List<?>) addressSection.get("fields");
    }

    private List<?> sections(Map<String, Object> response) {
        Map<String, Object> form = asMap(response.get("form"));
        return (List<?>) form.get("sections");
    }

    private BffPagesProperties pageConfig() {
        var properties = new BffPagesProperties();
        var page = new PageSchemaProperties();
        var metadata = new PageMetadataProperties();
        metadata.setId("personalInformationPage");
        metadata.setVersion("1.0");
        metadata.setTitle(Map.of("en", "Personal Information"));
        page.setMetadata(metadata);

        var form = new FormMetadataProperties();
        form.setId("personalInformationForm");
        form.setDefaultMode("view");

        var section = new SectionProperties();
        section.setId("addressInformation");
        section.setTitle(Map.of("en", "Address Information"));
        section.setFields(List.of(
                field("residentialAddressLine1", "Residential Address Line 1", "addr1", "addr1"),
                field("emailAddress", "Email Address", "email", "email"),
                field("applyToAllMemberAccounts", "Apply update to all", null, "ui-check-box-apply-all-member")));
        form.setSections(List.of(section));
        page.setForm(form);
        properties.setPages(Map.of("personalInformation", page));
        return properties;
    }

    private FieldProperties field(String id, String label, String dataKey, String configKey) {
        var field = new FieldProperties();
        field.setId(id);
        field.setLabel(Map.of("en", label));
        field.setDataType("string");
        field.setControlType("text");
        var binding = new ApimBindingProperties();
        binding.setData(dataKey);
        binding.setConfig(configKey);
        field.setApimBinding(binding);
        return field;
    }
}