package com.bct.ngtpa.apiservice.adapter.in.web.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bct.ngtpa.apiservice.adapter.in.web.pageconfig.ActionProperties;
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
import com.bct.ngtpa.apiservice.infrastructure.logging.LoggingSanitizer;
import com.bct.ngtpa.apiservice.infrastructure.logging.LoggingSanitizerProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PersonalInformationFieldMapperImplTest {

    private final PersonalInformationFieldMapperImpl mapper = new PersonalInformationFieldMapperImpl(
            new LoggingSanitizer(new ObjectMapper(), new LoggingSanitizerProperties()));

    @Test
    @SuppressWarnings("unchecked")
    void mapsVisibleEditableReadonlyFieldsActionsConfirmationAndValidationRules() {
        Map<String, Object> result = mapper.map(
                Map.of("addr1", "1 Example Street", "addr2", "Flat A", "email", "a@b.test"),
                Map.of("addr1", "EDITABLE_COM", "addr2", "READONLY", "email", "EDITABLE_OPTION"),
                pageConfig(),
                "zh_HK");

        Map<String, Object> page = (Map<String, Object>) result.get("page");
        assertEquals("personalInformationPage", page.get("id"));
        assertEquals("個人資料", page.get("title"));
        assertEquals("zh_HK", page.get("lang"));

        Map<String, Object> form = (Map<String, Object>) result.get("form");
        assertEquals("personalInformationForm", form.get("id"));
        assertEquals("1.0", form.get("version"));
        assertEquals("view", form.get("mode"));

        List<Map<String, Object>> sections = (List<Map<String, Object>>) form.get("sections");
        assertEquals(1, sections.size());
        assertEquals("地址資料", sections.get(0).get("label"));

        List<Map<String, Object>> fields = (List<Map<String, Object>>) sections.get(0).get("fields");
        assertEquals(3, fields.size());
        assertField(fields.get(0), "residentialAddressLine1", "居住地址第一行", "1 Example Street", false, true, 10);
        assertField(fields.get(1), "residentialAddressLine2", "居住地址第二行", "Flat A", true, false, 20);
        assertField(fields.get(2), "emailAddress", "電郵地址", "a@b.test", false, false, 30);

        List<Map<String, Object>> validations = (List<Map<String, Object>>) fields.get(0).get("validations");
        assertEquals("required", validations.get(0).get("type"));
        assertEquals("請輸入居住地址。", validations.get(0).get("message"));
        assertEquals(Map.of("operator", "notBlank", "field", "residentialAddressLine1"), validations.get(0).get("when"));
        assertEquals(Map.of("operator", "fail", "targets", List.of("residentialAddressLine1")), validations.get(0).get("then"));

        Map<String, Object> actions = (Map<String, Object>) form.get("actions");
        assertEquals("儲存", ((Map<String, Object>) actions.get("save")).get("label"));
        assertEquals(Boolean.TRUE, ((Map<String, Object>) actions.get("save")).get("enabled"));

        Map<String, Object> confirmation = (Map<String, Object>) form.get("confirmation");
        assertEquals("你確定嗎?", confirmation.get("message"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void hidesFieldsWithHiddenConfigAndFallsBackToEnglishLabels() {
        Map<String, Object> result = mapper.map(
                Map.of("addr1", "1 Example Street", "addr2", "Flat A"),
                Map.of("addr1", "HIDDEN", "addr2", "READONLY"),
                pageConfig(),
                "fr-FR");

        Map<String, Object> form = (Map<String, Object>) result.get("form");
        List<Map<String, Object>> sections = (List<Map<String, Object>>) form.get("sections");
        List<Map<String, Object>> fields = (List<Map<String, Object>>) sections.get(0).get("fields");

        assertEquals(1, fields.size());
        assertEquals("residentialAddressLine2", fields.get(0).get("name"));
        assertEquals("Residential Address Line 2", fields.get(0).get("label"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void returnsEmptySafeShapeWhenPageConfigurationIsMissing() {
        Map<String, Object> result = mapper.map(Map.of(), Map.of("addr1", "READONLY"), new BffPagesProperties(), "en");

        Map<String, Object> page = (Map<String, Object>) result.get("page");
        Map<String, Object> form = (Map<String, Object>) result.get("form");

        assertEquals(null, page.get("id"));
        assertEquals("", page.get("title"));
        assertEquals(List.of(), form.get("sections"));
        assertEquals(Map.of(), form.get("actions"));
    }

    private static void assertField(Map<String, Object> field, String name, String label, Object value,
                                    boolean readonly, boolean required, int displayOrder) {
        assertEquals(name, field.get("name"));
        assertEquals(label, field.get("label"));
        assertEquals(value, field.get("value"));
        assertEquals(value, field.get("originalValue"));
        assertEquals(readonly, field.get("readonly"));
        assertEquals(required, field.get("required"));
        assertEquals(displayOrder, field.get("displayOrder"));
        assertTrue(field.containsKey("dataType"));
        assertTrue(field.containsKey("controlType"));
    }

    private static BffPagesProperties pageConfig() {
        BffPagesProperties root = new BffPagesProperties();
        PageSchemaProperties page = new PageSchemaProperties();

        PageMetadataProperties metadata = new PageMetadataProperties();
        metadata.setId("personalInformationPage");
        metadata.setVersion("1.0");
        metadata.setTitle(Map.of("en", "Personal Information", "zh_HK", "個人資料"));
        page.setMetadata(metadata);

        FormMetadataProperties form = new FormMetadataProperties();
        form.setId("personalInformationForm");
        form.setDefaultMode("view");
        ActionProperties save = new ActionProperties();
        save.setName("save");
        save.setLabel(Map.of("en", "Save", "zh_HK", "儲存"));
        form.setActions(List.of(save));

        SectionProperties section = new SectionProperties();
        section.setId("addressInformation");
        section.setTitle(Map.of("en", "Address Information", "zh_HK", "地址資料"));
        section.setFields(List.of(
                field("residentialAddressLine1", 10, "Residential Address Line 1", "居住地址第一行", true),
                field("residentialAddressLine2", 20, "Residential Address Line 2", "居住地址第二行", false),
                field("emailAddress", 30, "Email Address", "電郵地址", false)));
        form.setSections(List.of(section));
        page.setForm(form);

        ConfirmationProperties confirmation = new ConfirmationProperties();
        confirmation.setMessage(Map.of("en", "Are you sure?", "zh_HK", "你確定嗎?"));
        page.setConfirmation(confirmation);

        root.setPages(Map.of("personalInformation", page));
        return root;
    }

    private static FieldProperties field(String id, int order, String en, String zh, boolean withValidation) {
        FieldProperties field = new FieldProperties();
        field.setId(id);
        field.setDisplayOrder(order);
        field.setLabel(Map.of("en", en, "zh_HK", zh));
        field.setDataType("string");
        field.setControlType("text");
        field.setPlaceholder(Map.of("en", en, "zh_HK", zh));
        field.setMaxLength(40);
        if (withValidation) {
            ValidationRuleProperties rule = new ValidationRuleProperties();
            rule.setId("required-rule");
            rule.setType("required");
            rule.setSeverity("error");
            rule.setCode("required.code");
            rule.setMessage(Map.of("en", "Please input a residential address.", "zh_HK", "請輸入居住地址。"));
            RuleConditionProperties when = new RuleConditionProperties();
            when.setOperator("notBlank");
            when.setField(id);
            rule.setWhen(when);
            RuleActionProperties then = new RuleActionProperties();
            then.setOperator("fail");
            then.setTargets(List.of(id));
            rule.setThen(then);
            field.setValidations(List.of(rule));
        }
        return field;
    }
}
