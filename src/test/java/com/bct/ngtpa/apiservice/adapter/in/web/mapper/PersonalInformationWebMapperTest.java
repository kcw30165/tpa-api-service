package com.bct.ngtpa.apiservice.adapter.in.web.mapper;

import com.bct.ngtpa.apiservice.adapter.in.web.pageconfig.ActionProperties;
import com.bct.ngtpa.apiservice.adapter.in.web.pageconfig.ApimBindingProperties;
import com.bct.ngtpa.apiservice.adapter.in.web.pageconfig.BffPagesProperties;
import com.bct.ngtpa.apiservice.adapter.in.web.pageconfig.ConfirmationProperties;
import com.bct.ngtpa.apiservice.adapter.in.web.pageconfig.FieldProperties;
import com.bct.ngtpa.apiservice.adapter.in.web.pageconfig.FormMetadataProperties;
import com.bct.ngtpa.apiservice.adapter.in.web.pageconfig.PageMetadataProperties;
import com.bct.ngtpa.apiservice.adapter.in.web.pageconfig.PageSchemaProperties;
import com.bct.ngtpa.apiservice.adapter.in.web.pageconfig.RuleActionProperties;
import com.bct.ngtpa.apiservice.adapter.in.web.pageconfig.RuleConditionProperties;
import com.bct.ngtpa.apiservice.adapter.in.web.pageconfig.SectionProperties;
import com.bct.ngtpa.apiservice.adapter.in.web.pageconfig.ValidationRuleProperties;
import com.bct.ngtpa.apiservice.application.dto.MemberInfoConfigItem;
import com.bct.ngtpa.apiservice.application.dto.MemberInfoConfigItemType;
import com.bct.ngtpa.apiservice.application.dto.PersonalInformationResult;
import com.bct.ngtpa.apiservice.infrastructure.logging.LoggingSanitizer;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class PersonalInformationWebMapperTest {

    @Test
    void mapsLegacyApimDataAndConfigMapsToBffFieldsUsingYamlBinding() {
        PersonalInformationWebMapper mapper = mapper(mock(LoggingSanitizer.class));
        var result = new PersonalInformationResult(
                Map.of("addr1", "ABC Street", "email", "nick@example.com"),
                Map.of("addr1", "EDITABLE_COM", "email", "READONLY"));

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
    void mapsDataConfigItemsToBffFieldsUsingApimDataItemId() {
        PersonalInformationWebMapper mapper = mapper(mock(LoggingSanitizer.class));
        var result = new PersonalInformationResult(
                Map.of("addr1", "ABC Street", "email", "nick@example.com"),
                Map.of(),
                Map.of(
                        "addr1", item("addr1", MemberInfoConfigItemType.DATA, "EDITABLE_COM"),
                        "email", item("email", MemberInfoConfigItemType.DATA, "READONLY")));

        Map<String, Object> response = mapper.toResponse(result, "en");

        List<?> fields = firstSectionFields(response);
        Map<String, Object> addressField = asMap(fields.get(0));
        Map<String, Object> emailField = asMap(fields.get(1));

        assertThat(addressField.get("value")).isEqualTo("ABC Street");
        assertThat(addressField.get("readonly")).isEqualTo(false);
        assertThat(addressField.get("required")).isEqualTo(true);
        assertThat(emailField.get("value")).isEqualTo("nick@example.com");
        assertThat(emailField.get("readonly")).isEqualTo(true);
        assertThat(emailField.get("required")).isEqualTo(false);
    }

    @Test
    void editableOptionIsEditableButNotRequired() {
        PersonalInformationWebMapper mapper = mapper(mock(LoggingSanitizer.class));
        var result = new PersonalInformationResult(
                Map.of("email", "nick@example.com"),
                Map.of(),
                Map.of("email", item("email", MemberInfoConfigItemType.DATA, "EDITABLE_OPTION")));

        Map<String, Object> response = mapper.toResponse(result, "en");

        Map<String, Object> emailField = asMap(firstSectionFields(response).get(0));
        assertThat(emailField.get("readonly")).isEqualTo(false);
        assertThat(emailField.get("required")).isEqualTo(false);
    }

    @Test
    void uiConfigItemAppliesConfigValueButDoesNotPopulateValueFromApimData() {
        PersonalInformationWebMapper mapper = mapper(mock(LoggingSanitizer.class));
        var result = new PersonalInformationResult(
                Map.of("ui-check-box-apply-all-member", true),
                Map.of(),
                Map.of("ui-check-box-apply-all-member",
                        item("ui-check-box-apply-all-member", MemberInfoConfigItemType.UI, "READONLY")));

        Map<String, Object> response = mapper.toResponse(result, "en");

        Map<String, Object> uiField = asMap(firstSectionFields(response).get(0));
        assertThat(uiField.get("name")).isEqualTo("applyToAllMemberAccounts");
        assertThat(uiField.get("value")).isNull();
        assertThat(uiField.get("readonly")).isEqualTo(true);
    }

    @Test
    void hiddenConfigItemIsOmittedAndEmptySectionIsOmittedFromFrontendResponse() {
        PersonalInformationWebMapper mapper = mapper(mock(LoggingSanitizer.class));
        var result = new PersonalInformationResult(
                Map.of("addr1", "ABC Street"),
                Map.of(),
                Map.of("addr1", item("addr1", MemberInfoConfigItemType.DATA, "HIDDEN")));

        Map<String, Object> response = mapper.toResponse(result, "en");

        assertThat(sections(response)).isEmpty();
    }

    @Test
    void ruleConfigItemIsIgnoredForNow() {
        PersonalInformationWebMapper mapper = mapper(mock(LoggingSanitizer.class));
        var result = new PersonalInformationResult(
                Map.of("addr1", "ABC Street"),
                Map.of(),
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
                Map.of(),
                Map.of("addr1", item("addr1", MemberInfoConfigItemType.UNKNOWN, "EDITABLE_COM")));

        Map<String, Object> response = mapper.toResponse(result, "en");

        assertThat(sections(response)).isEmpty();
        verify(sanitizer, atLeastOnce()).toSafeString(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void nullResultAndMissingPageConfigurationReturnStableEmptyFormAndPageSections() {
        var mapper = new PersonalInformationWebMapper(mock(LoggingSanitizer.class), new BffPagesProperties());

        Map<String, Object> response = mapper.toResponse((PersonalInformationResult) null, "en");

        assertThat(asMap(response.get("page"))).containsEntry("id", null)
                .containsEntry("lang", "en")
                .containsEntry("title", "");
        Map<String, Object> form = asMap(response.get("form"));
        assertThat(form).containsEntry("sections", List.of());
        assertThat(form).containsEntry("actions", Map.of());
        assertThat(form).containsEntry("confirmation", Map.of());
    }

    @Test
    void richPageSchemaMapsFallbackLabelsActionsValidationRulesCopyRulesAndConfirmation() {
        var mapper = new PersonalInformationWebMapper(mock(LoggingSanitizer.class), richPageConfig());
        var result = new PersonalInformationResult(
                Map.of("addr1", "1 Branch Street"),
                Map.of("addr1", "EDITABLE_COM", "addr2", "READONLY", "country", "EDITABLE_OPTION"),
                Map.of());

        Map<String, Object> response = mapper.toResponse(result, "zh_HK");

        assertThat(asMap(response.get("page"))).containsEntry("id", "personalInformationPage")
                .containsEntry("title", "個人資料-from-zh-HK-key");

        Map<String, Object> form = asMap(response.get("form"));
        assertThat(form).containsEntry("id", "personalInformationForm")
                .containsEntry("mode", "edit")
                .containsEntry("version", "1.0");
        assertThat(asMap(form.get("actions"))).containsKey("submit");
        assertThat(asMap(asMap(form.get("actions")).get("submit"))).containsEntry("label", "遞交");
        assertThat(asMap(form.get("confirmation"))).containsEntry("message", "請確認");

        Map<String, Object> section = asMap(asList(form.get("sections")).get(0));
        assertThat(section).containsEntry("id", "addressInformation")
                .containsEntry("label", "地址資料");

        List<?> fields = asList(section.get("fields"));
        Map<String, Object> address1 = asMap(fields.get(0));
        assertThat(address1).containsEntry("name", "residentialAddressLine1")
                .containsEntry("value", "1 Branch Street")
                .containsEntry("originalValue", "1 Branch Street")
                .containsEntry("readonly", false)
                .containsEntry("required", true)
                .containsEntry("displayOrder", 10)
                .containsEntry("placeholder", "First available label");
        assertThat(address1).containsKeys("minLength", "maxLength", "pattern", "optionSource", "copyWhenChecked",
                "validations");

        Map<String, Object> validation = asMap(asList(address1.get("validations")).get(0));
        assertThat(validation).containsEntry("id", "v1")
                .containsEntry("type", "conditionalRequired")
                .containsEntry("value", 5)
                .containsEntry("severity", "error")
                .containsEntry("code", "addr.required")
                .containsEntry("message", "必填");
        assertThat(asMap(validation.get("when"))).containsEntry("operator", "notBlank")
                .containsEntry("field", "residentialAddressLine1")
                .containsEntry("fields", List.of("residentialAddressLine1", "residentialCountry"))
                .containsEntry("fieldRequired", true)
                .containsEntry("compareWith", "originalValue")
                .containsKeys("conditions", "groups");
        assertThat(asMap(validation.get("then"))).containsEntry("operator", "allRequired")
                .containsEntry("field", "residentialCountry")
                .containsEntry("fields", List.of("residentialCountry"))
                .containsEntry("targets", List.of("residentialAddressLine1"));

        Map<String, Object> address2 = asMap(fields.get(1));
        assertThat(address2).containsEntry("name", "residentialAddressLine2")
                .containsEntry("value", "")
                .containsEntry("readonly", true)
                .containsEntry("required", false)
                .containsEntry("displayOrder", 20);

        Map<String, Object> country = asMap(fields.get(2));
        assertThat(country).containsEntry("name", "residentialCountry")
                .containsEntry("readonly", false)
                .containsEntry("required", false)
                .containsEntry("displayOrder", 30);
    }

    @Test
    void fieldsWithoutBindingsOrHiddenConfigAreSkippedButDefaultDisplayOrderStillAdvances() {
        var mapper = new PersonalInformationWebMapper(mock(LoggingSanitizer.class), configWithSkippedFields());

        Map<String, Object> response = mapper.toResponse(Map.of("visible", "ok"), Map.of("visible", "EDITABLE_OPTION"),
                "en");

        List<?> fields = asList(asMap(asList(asMap(response.get("form")).get("sections")).get(0)).get("fields"));
        assertThat(fields).hasSize(1);
        assertThat(asMap(fields.get(0))).containsEntry("name", "visibleField")
                .containsEntry("displayOrder", 20);
    }

    @Test
    void nullPagesPropertiesAndNullPagesMapUseEmptyDefaults() {
        assertThat(asMap(new PersonalInformationWebMapper(mock(LoggingSanitizer.class), null)
                .toResponse(Map.of(), Map.of(), "en").get("page"))).containsEntry("id", null)
                .containsEntry("lang", "en")
                .containsEntry("title", "");

        var properties = new BffPagesProperties();
        properties.setPages(null);
        assertThat(asMap(new PersonalInformationWebMapper(mock(LoggingSanitizer.class), properties)
                .toResponse(Map.of(), Map.of(), "en").get("page"))).containsEntry("id", null)
                .containsEntry("lang", "en")
                .containsEntry("title", "");
    }

    @Test
    void formDefaultsToViewAndEmptySectionsWhenFormOrSectionsAreMissing() {
        var propertiesWithoutForm = properties(new PageSchemaProperties());
        Map<String, Object> responseWithoutForm = mapper(propertiesWithoutForm).toResponse(Map.of(), Map.of(), "fr-FR");
        Map<String, Object> formWithoutForm = asMap(responseWithoutForm.get("form"));
        assertThat(formWithoutForm).containsEntry("id", null)
                .containsEntry("mode", "view")
                .containsEntry("sections", List.of());

        var pageWithNullSections = new PageSchemaProperties();
        var form = new FormMetadataProperties();
        form.setId("form-with-null-sections");
        form.setSections(null);
        pageWithNullSections.setForm(form);
        Map<String, Object> responseWithNullSections = mapper(properties(pageWithNullSections)).toResponse(Map.of(),
                Map.of(), "en");
        assertThat(asMap(responseWithNullSections.get("form"))).containsEntry("sections", List.of());
    }

    @Test
    void sectionsWithNoRenderableFieldsAreOmittedAndLaterSectionKeepsAdvancedOrder() {
        var page = pageWithSections(
                section("empty", null),
                section("missing-state", List.of(boundField("missing", "missing", "missing", null))),
                section("visible-section", List.of(boundField("visible", "visible", "visible", null))));

        Map<String, Object> response = mapper(properties(page))
                .toResponse(Map.of("visible", "ok"), Map.of("visible", "EDITABLE_OPTION"), "en");

        List<?> sections = asList(asMap(response.get("form")).get("sections"));
        assertThat(sections).hasSize(1);
        assertThat(asMap(sections.get(0))).containsEntry("id", "visible-section")
                .containsEntry("displayOrder", 30);
    }

    @Test
    void modernConfigItemsSkipNullBlankHiddenRuleUnknownMissingAndRenderDataAndUiItems() {
        LoggingSanitizer sanitizer = mock(LoggingSanitizer.class);
        var page = pageWithSections(section("main", List.of(
                boundField("dataField", null, "shared", null),
                boundField("duplicateField", "ignored-duplicate-data", "shared", null),
                boundField("uiField", null, "ui-config", null),
                boundField("hiddenField", "hidden", "hidden", null))));

        Map<String, MemberInfoConfigItem> configItems = new LinkedHashMap<>();
        configItems.put("null", null);
        configItems.put("blank", MemberInfoConfigItem.of(" ", MemberInfoConfigItemType.DATA, "EDITABLE_COM"));
        configItems.put("unknown", MemberInfoConfigItem.of("shared", MemberInfoConfigItemType.UNKNOWN, "EDITABLE_COM"));
        configItems.put("missing",
                MemberInfoConfigItem.of("missing-config", MemberInfoConfigItemType.DATA, "EDITABLE_COM"));
        configItems.put("rule", MemberInfoConfigItem.of("shared", MemberInfoConfigItemType.RULE, "EDITABLE_COM"));
        configItems.put("hidden", MemberInfoConfigItem.of("hidden", MemberInfoConfigItemType.DATA, "HIDDEN"));
        configItems.put("data", MemberInfoConfigItem.of("shared", MemberInfoConfigItemType.DATA, "EDITABLE_COM"));
        configItems.put("ui", MemberInfoConfigItem.of("ui-config", MemberInfoConfigItemType.UI, "READONLY"));

        Map<String, Object> response = new PersonalInformationWebMapper(sanitizer, properties(page))
                .toResponse(Map.of("shared", "data-value", "ui-config", "ignored-ui-value"), Map.of(), configItems,
                        "en");

        List<?> fields = fields(response);
        assertThat(fields).hasSize(2);
        assertThat(asMap(fields.get(0))).containsEntry("name", "dataField")
                .containsEntry("value", "data-value")
                .containsEntry("required", true);
        assertThat(asMap(fields.get(1))).containsEntry("name", "uiField")
                .containsEntry("value", null)
                .containsEntry("readonly", true);
        verify(sanitizer, atLeastOnce()).toSafeString(any());
    }

    @Test
    void legacyConfigNullValueAndMissingApimDataRenderEmptyValueAndNoReadonlyRequiredFlags() {
        var page = pageWithSections(section("main", List.of(
                boundField("field", "data", "config", 99))));
        Map<String, String> legacyConfig = new LinkedHashMap<>();
        legacyConfig.put("config", null);

        Map<String, Object> response = mapper(properties(page)).toResponse(Map.of(), legacyConfig, "en");

        Map<String, Object> field = asMap(fields(response).get(0));
        assertThat(field).containsEntry("value", "")
                .containsEntry("originalValue", "")
                .containsEntry("readonly", false)
                .containsEntry("required", false)
                .containsEntry("displayOrder", 99);
    }

    @Test
    void skipsNullAndBlankSectionsAndRendersFallbackViewMode() {
        var page = new PageSchemaProperties();
        var metadata = new PageMetadataProperties();
        metadata.setId("page-id");
        page.setMetadata(metadata);
        var form = new FormMetadataProperties();
        form.setId("form-id");
        form.setDefaultMode(" ");
        form.setSections(Arrays.asList(null, section(" ", List.of(field("blank", "blank", "blank"))),
                section("visible", List.of(field("field", "field", "field")))));
        page.setForm(form);

        Map<String, Object> response = mapper(page).toResponse(Map.of("field", "value"),
                Map.of("field", "EDITABLE_OPTION"), "en");

        Map<String, Object> formResponse = asMap(response.get("form"));
        assertThat(formResponse).containsEntry("mode", "view");
        List<?> sections = asList(formResponse.get("sections"));
        assertThat(sections).hasSize(1);
        assertThat(asMap(sections.get(0))).containsEntry("id", "visible");
    }

    @Test
    void skipsFieldsWithNullBindingBlankConfigAndNullIdWhileRenderingLaterField() {
        var page = new PageSchemaProperties();
        page.setMetadata(new PageMetadataProperties());
        var form = new FormMetadataProperties();
        form.setSections(List.of(section("main", Arrays.asList(
                null,
                field(null, "x", "x"),
                fieldWithoutBinding("no-binding"),
                field("blank-config", "data", " "),
                field("visible", "visible", "visible")))));
        page.setForm(form);

        Map<String, Object> response = mapper(page).toResponse(Map.of("visible", "ok"),
                Map.of("visible", "EDITABLE_OPTION"), "en");

        List<?> fields = fields(response);
        assertThat(fields).hasSize(1);
        assertThat(asMap(fields.get(0))).containsEntry("name", "visible")
                .containsEntry("displayOrder", 30);
    }

    @Test
    void validationRulesSkipNullRuleNullConditionNullActionAndEmptyMessages() {
        var field = field("visible", "visible", "visible");
        var rule = new ValidationRuleProperties();
        rule.setId("v1");
        rule.setWhen(null);
        rule.setThen(null);
        rule.setMessage(Map.of());
        field.setValidations(Arrays.asList(null, rule));

        var page = new PageSchemaProperties();
        page.setMetadata(new PageMetadataProperties());
        var form = new FormMetadataProperties();
        form.setSections(List.of(section("main", List.of(field))));
        page.setForm(form);
        page.setValidations(Arrays.asList(null, rule));

        Map<String, Object> response = mapper(page).toResponse(Map.of("visible", "ok"),
                Map.of("visible", "EDITABLE_OPTION"), "en");

        Map<String, Object> renderedField = asMap(fields(response).get(0));
        assertThat(asList(renderedField.get("validations"))).hasSize(1);
        assertThat(asMap(asList(renderedField.get("validations")).get(0))).containsEntry("id", "v1")
                .doesNotContainKeys("when", "then", "message");
        assertThat(asList(asMap(response.get("form")).get("validationRules"))).hasSize(1);
    }

    @Test
    void confirmationAndActionsWithEmptyConfigurationRemainStable() {
        var page = new PageSchemaProperties();
        page.setMetadata(new PageMetadataProperties());
        var form = new FormMetadataProperties();
        ActionProperties blankAction = new ActionProperties();
        blankAction.setName(" ");
        ActionProperties saveAction = new ActionProperties();
        saveAction.setName("save");
        saveAction.setLabel(Map.of());
        form.setActions(Arrays.asList(null, blankAction, saveAction));
        form.setSections(List.of(section("main", List.of(field("visible", "visible", "visible")))));
        page.setForm(form);
        ConfirmationProperties confirmation = new ConfirmationProperties();
        confirmation.setMessage(Map.of());
        page.setConfirmation(confirmation);

        Map<String, Object> response = mapper(page).toResponse(Map.of("visible", "ok"),
                Map.of("visible", "EDITABLE_OPTION"), "en");

        Map<String, Object> formResponse = asMap(response.get("form"));
        assertThat(asMap(formResponse.get("confirmation"))).isEmpty();
        assertThat(asMap(asMap(formResponse.get("actions")).get("save"))).containsEntry("label", "");
    }

    @Test
    void nullDataMapNullConfigAndNullLabelValuesResolveToEmptyOutputValues() {
        var field = field("visible", "visible", "visible");
        Map<String, String> label = new LinkedHashMap<>();
        label.put("en", null);
        field.setLabel(label);
        var page = new PageSchemaProperties();
        page.setMetadata(new PageMetadataProperties());
        var form = new FormMetadataProperties();
        form.setSections(List.of(section("main", List.of(field))));
        page.setForm(form);

        Map<String, Object> response = mapper(page).toResponse(null, Map.of("visible", "READONLY"), "en");

        Map<String, Object> rendered = asMap(fields(response).get(0));
        assertThat(rendered).containsEntry("label", "")
                .containsEntry("value", "")
                .containsEntry("readonly", true);
    }
    // ── Helpers ───────────────────────────────────────────────────────────────

    private static BffPagesProperties richPageConfig() {
        var properties = new BffPagesProperties();
        var page = new PageSchemaProperties();

        var metadata = new PageMetadataProperties();
        metadata.setId("personalInformationPage");
        metadata.setVersion("1.0");
        metadata.setTitle(Map.of("zh-HK", "個人資料-from-zh-HK-key", "en", "Personal Information"));
        page.setMetadata(metadata);

        var form = new FormMetadataProperties();
        form.setId("personalInformationForm");
        form.setDefaultMode("edit");
        form.setActions(Arrays.asList(action(null), action(""), action("submit")));

        var section = new SectionProperties();
        section.setId("addressInformation");
        section.setTitle(Map.of("zh_HK", "地址資料", "en", "Address Information"));
        section.setFields(List.of(
                richField("residentialAddressLine1", "addr1", "addr1", null),
                simpleField("residentialAddressLine2", "addr2", "addr2", null),
                simpleField("residentialCountry", "country", "country", 30)));
        form.setSections(Arrays.asList(null, section));
        page.setForm(form);

        var confirmation = new ConfirmationProperties();
        confirmation.setMessage(Map.of("zh_HK", "請確認", "en", "Please confirm"));
        page.setConfirmation(confirmation);

        properties.setPages(Map.of("personalInformation", page));
        return properties;
    }

    private static FieldProperties richField(String id, String data, String config, Integer displayOrder) {
        FieldProperties field = simpleField(id, data, config, displayOrder);
        field.setLabel(Map.of("fr", "First available label"));
        field.setPlaceholder(Map.of("fr", "First available label"));
        field.setMinLength(5);
        field.setMaxLength(40);
        field.setPattern("^[A-Z].*$");
        field.setOptionSource("countries");
        field.setCopyWhenChecked(Map.of("fromTo", Map.of("a", "b")));
        field.setValidations(Arrays.asList(null, validationRule()));
        return field;
    }

    private static FieldProperties simpleField(String id, String data, String config, Integer displayOrder) {
        var field = new FieldProperties();
        field.setId(id);
        field.setLabel(Map.of("zh_HK", "欄位", "en", id != null ? id : ""));
        field.setDataType("string");
        field.setControlType("text");
        field.setDisplayOrder(displayOrder);
        var binding = new ApimBindingProperties();
        binding.setData(data);
        binding.setConfig(config);
        field.setApimBinding(binding);
        return field;
    }

    private static ValidationRuleProperties validationRule() {
        var rule = new ValidationRuleProperties();
        rule.setId("v1");
        rule.setType("conditionalRequired");
        rule.setValue(5);
        rule.setSeverity("error");
        rule.setCode("addr.required");
        rule.setMessage(Map.of("zh_HK", "必填", "en", "Required"));

        var nested = new RuleConditionProperties();
        nested.setOperator("blank");
        nested.setField("mailingAddressLine1");
        var when = new RuleConditionProperties();
        when.setOperator("notBlank");
        when.setField("residentialAddressLine1");
        when.setFields(List.of("residentialAddressLine1", "residentialCountry"));
        when.setFieldRequired(true);
        when.setCompareWith("originalValue");
        when.setConditions(List.of(nested));
        when.setGroups(List.of(Map.of("id", "residentialAddress")));
        rule.setWhen(when);

        var then = new RuleActionProperties();
        then.setOperator("allRequired");
        then.setField("residentialCountry");
        then.setFields(List.of("residentialCountry"));
        then.setTargets(List.of("residentialAddressLine1"));
        rule.setThen(then);
        return rule;
    }

    private static ActionProperties action(String name) {
        var action = new ActionProperties();
        action.setName(name);
        action.setLabel(Map.of("zh_HK", "遞交", "en", "Submit"));
        return action;
    }

    private static BffPagesProperties configWithSkippedFields() {
        var properties = new BffPagesProperties();
        var page = new PageSchemaProperties();
        page.setMetadata(new PageMetadataProperties());
        var form = new FormMetadataProperties();
        var section = new SectionProperties();
        section.setId("section");
        FieldProperties nullId = simpleField(null, "x", "x", null);
        FieldProperties noState = simpleField("noState", "missing", "missing", null);
        FieldProperties visible = simpleField("visibleField", "visible", "visible", null);
        section.setFields(List.of(nullId, noState, visible));
        form.setSections(List.of(section));
        page.setForm(form);
        properties.setPages(Map.of("personalInformation", page));
        return properties;
    }

    private PersonalInformationWebMapper mapper(LoggingSanitizer sanitizer) {
        return new PersonalInformationWebMapper(sanitizer, pageConfig());
    }

    private static PersonalInformationWebMapper mapper(PageSchemaProperties page) {
        var properties = new BffPagesProperties();
        properties.setPages(Map.of("personalInformation", page));
        return new PersonalInformationWebMapper(mock(LoggingSanitizer.class), properties);
    }

    private MemberInfoConfigItem item(String itemId, MemberInfoConfigItemType itemType, String configValue) {
        return MemberInfoConfigItem.of(itemId, itemType, configValue);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object value) {
        return (Map<String, Object>) value;
    }

    private static List<?> firstSectionFields(Map<String, Object> response) {
        List<?> sections = sections(response);
        Map<String, Object> section = asMap(sections.get(0));
        return (List<?>) section.get("fields");
    }

    private static List<?> sections(Map<String, Object> response) {
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

    private static FieldProperties field(String id, String data, String config) {
        var field = new FieldProperties();
        field.setId(id);
        field.setLabel(Map.of("en", id == null ? "" : id));
        field.setDataType("string");
        field.setControlType("text");
        var binding = new ApimBindingProperties();
        binding.setData(data);
        binding.setConfig(config);
        field.setApimBinding(binding);
        return field;
    }

    private static List<?> asList(Object value) {
        return (List<?>) value;
    }

    private static List<?> fields(Map<String, Object> response) {
        return asList(asMap(asList(asMap(response.get("form")).get("sections")).get(0)).get("fields"));
    }

    private static BffPagesProperties properties(PageSchemaProperties page) {
        var properties = new BffPagesProperties();
        properties.setPages(Map.of("personalInformation", page));
        return properties;
    }

    private static PersonalInformationWebMapper mapper(BffPagesProperties properties) {
        return new PersonalInformationWebMapper(mock(LoggingSanitizer.class), properties);
    }

    private static SectionProperties section(String id, List<FieldProperties> fields) {
        var section = new SectionProperties();
        section.setId(id);
        section.setTitle(Map.of("en", id));
        section.setFields(fields);
        return section;
    }

    private static FieldProperties boundField(String id, String dataKey, String configKey, Integer displayOrder) {
        var field = new FieldProperties();
        field.setId(id);
        field.setLabel(Map.of("en", id));
        field.setDataType("string");
        field.setControlType("text");
        field.setDisplayOrder(displayOrder);
        var binding = new ApimBindingProperties();
        binding.setData(dataKey);
        binding.setConfig(configKey);
        field.setApimBinding(binding);
        return field;
    }

    private static PageSchemaProperties pageWithSections(SectionProperties... sections) {
        var page = new PageSchemaProperties();
        var metadata = new PageMetadataProperties();
        metadata.setVersion("1.0");
        page.setMetadata(metadata);
        var form = new FormMetadataProperties();
        form.setId("form");
        form.setSections(Arrays.asList(sections));
        page.setForm(form);
        return page;
    }

    private static FieldProperties fieldWithoutBinding(String id) {
        var field = new FieldProperties();
        field.setId(id);
        field.setLabel(Map.of("en", id));
        return field;
    }
}
