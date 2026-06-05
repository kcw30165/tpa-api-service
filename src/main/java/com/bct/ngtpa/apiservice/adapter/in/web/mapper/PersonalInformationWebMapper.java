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

import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
@Slf4j
public class PersonalInformationWebMapper {

    private static final String PAGE_KEY = "personalInformation";
    private static final String DEFAULT_LANGUAGE = "en";
    private static final String ZH_HK_LANGUAGE = "zh_HK";

    private final LoggingSanitizer loggingSanitizer;
    private final BffPagesProperties bffPagesProperties;

    public PersonalInformationWebMapper(
            LoggingSanitizer loggingSanitizer,
            BffPagesProperties bffPagesProperties) {
        this.loggingSanitizer = loggingSanitizer;
        this.bffPagesProperties = bffPagesProperties;
    }

    public Map<String, Object> toResponse(PersonalInformationResult result, String language) {
        Map<String, Object> apimData = result != null ? result.data() : Map.of();
        Map<String, String> apimConfig = result != null ? result.config() : Map.of();
        Map<String, MemberInfoConfigItem> apimConfigItems = result != null ? result.configItems() : Map.of();
        return toResponse(apimData, apimConfig, apimConfigItems, language);
    }

    public Map<String, Object> toResponse(
            Map<String, Object> apimData,
            Map<String, String> apimConfig,
            String language) {
        return toResponse(apimData, apimConfig, Map.of(), language);
    }

    public Map<String, Object> toResponse(
            Map<String, Object> apimData,
            Map<String, String> apimConfig,
            Map<String, MemberInfoConfigItem> apimConfigItems,
            String language) {
        PageSchemaProperties pageSchema = resolvePageSchema();
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("page", buildPage(pageSchema, language));
        response.put("form", buildForm(pageSchema, apimData, apimConfig, apimConfigItems, language));
        return response;
    }

    private PageSchemaProperties resolvePageSchema() {
        if (bffPagesProperties == null || bffPagesProperties.getPages() == null) {
            log.error("bff-pages configuration is missing");
            return null;
        }
        PageSchemaProperties pageSchema = bffPagesProperties.getPages().get(PAGE_KEY);
        if (pageSchema == null) {
            log.error("{} page schema not found", PAGE_KEY);
        }
        return pageSchema;
    }

    private Map<String, Object> buildPage(PageSchemaProperties pageSchema, String language) {
        Map<String, Object> page = new LinkedHashMap<>();
        PageMetadataProperties metadata = pageSchema != null ? pageSchema.getMetadata() : null;
        page.put("id", metadata != null ? metadata.getId() : null);
        page.put("title", resolveLabel(metadata != null ? metadata.getTitle() : null, language));
        page.put("lang", language);
        return page;
    }

    private Map<String, Object> buildForm(PageSchemaProperties pageSchema,
                                          Map<String, Object> apimData,
                                          Map<String, String> apimConfig,
                                          Map<String, MemberInfoConfigItem> apimConfigItems,
                                          String language) {
        Map<String, Object> form = new LinkedHashMap<>();
        FormMetadataProperties formSchema = pageSchema != null ? pageSchema.getForm() : null;
        PageMetadataProperties metadata = pageSchema != null ? pageSchema.getMetadata() : null;
        form.put("id", formSchema != null ? formSchema.getId() : null);
        form.put("version", metadata != null ? metadata.getVersion() : null);
        form.put("mode", formSchema != null && hasText(formSchema.getDefaultMode())
                ? formSchema.getDefaultMode()
                : "view");
        form.put("sections", buildSections(pageSchema, apimData, apimConfig, apimConfigItems, language));
        form.put("validationRules", mapValidationRules(
                pageSchema != null ? pageSchema.getValidations() : null,
                language));
        form.put("confirmation", buildConfirmation(pageSchema != null ? pageSchema.getConfirmation() : null, language));
        form.put("actions", buildActions(formSchema, language));
        return form;
    }

    private List<Map<String, Object>> buildSections(PageSchemaProperties pageSchema,
                                                    Map<String, Object> apimData,
                                                    Map<String, String> apimConfig,
                                                    Map<String, MemberInfoConfigItem> apimConfigItems,
                                                    String language) {
        if (pageSchema == null || pageSchema.getForm() == null || pageSchema.getForm().getSections() == null) {
            return List.of();
        }
        Map<String, FieldState> fieldStates = buildFieldStates(pageSchema, apimData, apimConfig, apimConfigItems);
        List<Map<String, Object>> sections = new ArrayList<>();
        int defaultSectionOrder = 10;
        for (SectionProperties sectionSchema : pageSchema.getForm().getSections()) {
            if (sectionSchema == null || !hasText(sectionSchema.getId())) {
                continue;
            }
            Map<String, Object> section = new LinkedHashMap<>();
            section.put("id", sectionSchema.getId());
            section.put("label", resolveLabel(sectionSchema.getTitle(), language));
            section.put("displayOrder", defaultSectionOrder);
            List<Map<String, Object>> fields = buildSectionFields(sectionSchema, fieldStates, language);
            if (fields.isEmpty()) {
                defaultSectionOrder += 10;
                continue;
            }
            section.put("fields", fields);
            sections.add(section);
            defaultSectionOrder += 10;
        }
        return sections;
    }

    private Map<String, FieldState> buildFieldStates(PageSchemaProperties pageSchema,
                                                     Map<String, Object> apimData,
                                                     Map<String, String> apimConfig,
                                                     Map<String, MemberInfoConfigItem> apimConfigItems) {
        Map<String, FieldState> fieldStates = new LinkedHashMap<>();
        Map<String, BoundField> fieldsByConfigItemId = buildFieldsByConfigItemId(pageSchema);
        Set<String> seenApimConfigItems = new HashSet<>();

        if (apimConfigItems != null && !apimConfigItems.isEmpty()) {
            for (MemberInfoConfigItem configItem : apimConfigItems.values()) {
                if (configItem == null || !hasText(configItem.itemId())) {
                    continue;
                }
                String configKey = configItem.itemId();
                String configValue = configItem.configValue();
                seenApimConfigItems.add(configKey);

                MemberInfoConfigItemType itemType = configItem.itemType();
                if (itemType == null || itemType == MemberInfoConfigItemType.UNKNOWN) {
                    logUnknownConfigItemType(configKey, configValue, configItem);
                    continue;
                }
                if (itemType == MemberInfoConfigItemType.RULE) {
                    continue;
                }

                BoundField boundField = fieldsByConfigItemId.get(configKey);
                if (boundField == null) {
                    logMissingYamlBinding(configKey, configValue);
                    continue;
                }
                if ("HIDDEN".equalsIgnoreCase(configValue)) {
                    continue;
                }
                Object value = itemType == MemberInfoConfigItemType.DATA
                        ? resolveValue(apimData, configKey)
                        : null;
                fieldStates.put(
                        boundField.field().getId(),
                        new FieldState(configKey, configValue, value, boundField.location()));
            }
        } else if (apimConfig != null) {
            // Backward-compatible legacy path for the old Map<String, String> config shape.
            for (Map.Entry<String, String> cfg : apimConfig.entrySet()) {
                String configKey = cfg.getKey();
                String configValue = cfg.getValue();
                seenApimConfigItems.add(configKey);

                BoundField boundField = fieldsByConfigItemId.get(configKey);
                if (boundField == null) {
                    logMissingYamlBinding(configKey, configValue);
                    continue;
                }
                if ("HIDDEN".equalsIgnoreCase(configValue)) {
                    continue;
                }
                Object value = resolveValue(apimData, boundField.dataKey());
                fieldStates.put(
                        boundField.field().getId(),
                        new FieldState(configKey, configValue, value, boundField.location()));
            }
        }

        logMissingExpectedApimConfig(fieldsByConfigItemId, seenApimConfigItems);
        return fieldStates;
    }

    private Map<String, BoundField> buildFieldsByConfigItemId(PageSchemaProperties pageSchema) {
        Map<String, BoundField> fieldsByConfigItemId = new LinkedHashMap<>();
        if (pageSchema == null || pageSchema.getForm() == null || pageSchema.getForm().getSections() == null) {
            return fieldsByConfigItemId;
        }
        for (SectionProperties section : pageSchema.getForm().getSections()) {
            if (section == null || section.getFields() == null) {
                continue;
            }
            for (FieldProperties field : section.getFields()) {
                if (field == null || !hasText(field.getId())) {
                    continue;
                }
                ApimBindingProperties binding = field.getApimBinding();
                if (binding == null || !hasText(binding.getConfig())) {
                    continue;
                }
                String dataKey = hasText(binding.getData()) ? binding.getData() : binding.getConfig();
                FieldLocation location = new FieldLocation(section.getId(), field);
                BoundField previous = fieldsByConfigItemId.putIfAbsent(
                        binding.getConfig(),
                        new BoundField(field, location, dataKey));
                if (previous != null) {
                    logDuplicateYamlBinding(binding.getConfig(), previous.field().getId(), field.getId());
                }
            }
        }
        return fieldsByConfigItemId;
    }

    private List<Map<String, Object>> buildSectionFields(SectionProperties sectionSchema,
                                                         Map<String, FieldState> fieldStates,
                                                         String language) {
        if (sectionSchema.getFields() == null) {
            return List.of();
        }
        List<Map<String, Object>> fields = new ArrayList<>();
        int defaultFieldOrder = 10;
        for (FieldProperties fieldSchema : sectionSchema.getFields()) {
            if (fieldSchema == null || !hasText(fieldSchema.getId())) {
                continue;
            }
            FieldState state = fieldStates.get(fieldSchema.getId());
            if (state == null) {
                defaultFieldOrder += 10;
                continue;
            }
            Map<String, Object> field = new LinkedHashMap<>();
            field.put("name", fieldSchema.getId());
            field.put("label", resolveLabel(fieldSchema.getLabel(), language));
            putIfHasText(field, "dataType", fieldSchema.getDataType());
            putIfHasText(field, "controlType", fieldSchema.getControlType());
            field.put("value", state.value());
            field.put("originalValue", state.value());
            field.put("readonly", isReadonly(state.configValue()));
            field.put("required", isRequired(state.configValue()));
            putIfNotNull(field, "minLength", fieldSchema.getMinLength());
            putIfNotNull(field, "maxLength", fieldSchema.getMaxLength());
            putIfHasText(field, "pattern", fieldSchema.getPattern());
            putIfHasText(field, "placeholder", resolveLabel(fieldSchema.getPlaceholder(), language));
            putIfHasText(field, "optionSource", fieldSchema.getOptionSource());
            if (fieldSchema.getCopyWhenChecked() != null && !fieldSchema.getCopyWhenChecked().isEmpty()) {
                field.put("copyWhenChecked", fieldSchema.getCopyWhenChecked());
            }
            field.put("displayOrder", fieldSchema.getDisplayOrder() != null
                    ? fieldSchema.getDisplayOrder()
                    : defaultFieldOrder);
            List<Map<String, Object>> validations = mapValidationRules(fieldSchema.getValidations(), language);
            if (!validations.isEmpty()) {
                field.put("validations", validations);
            }
            fields.add(field);
            defaultFieldOrder += 10;
        }
        return fields;
    }

    private List<Map<String, Object>> mapValidationRules(List<ValidationRuleProperties> rules, String language) {
        if (rules == null || rules.isEmpty()) {
            return List.of();
        }
        List<Map<String, Object>> mappedRules = new ArrayList<>();
        for (ValidationRuleProperties rule : rules) {
            if (rule == null) {
                continue;
            }
            Map<String, Object> mappedRule = new LinkedHashMap<>();
            putIfHasText(mappedRule, "id", rule.getId());
            putIfHasText(mappedRule, "type", rule.getType());
            putIfNotNull(mappedRule, "value", rule.getValue());
            Map<String, Object> when = mapCondition(rule.getWhen());
            if (!when.isEmpty()) {
                mappedRule.put("when", when);
            }
            Map<String, Object> then = mapAction(rule.getThen());
            if (!then.isEmpty()) {
                mappedRule.put("then", then);
            }
            putIfHasText(mappedRule, "severity", rule.getSeverity());
            putIfHasText(mappedRule, "code", rule.getCode());
            putIfHasText(mappedRule, "message", resolveLabel(rule.getMessage(), language));
            mappedRules.add(mappedRule);
        }
        return mappedRules;
    }

    private Map<String, Object> mapCondition(RuleConditionProperties condition) {
        Map<String, Object> mapped = new LinkedHashMap<>();
        if (condition == null) {
            return mapped;
        }
        putIfHasText(mapped, "operator", condition.getOperator());
        putIfHasText(mapped, "field", condition.getField());
        putIfNotNull(mapped, "fields", condition.getFields());
        putIfNotNull(mapped, "fieldRequired", condition.getFieldRequired());
        putIfHasText(mapped, "compareWith", condition.getCompareWith());
        putIfNotNull(mapped, "conditions", condition.getConditions());
        putIfNotNull(mapped, "groups", condition.getGroups());
        return mapped;
    }

    private Map<String, Object> mapAction(RuleActionProperties action) {
        Map<String, Object> mapped = new LinkedHashMap<>();
        if (action == null) {
            return mapped;
        }
        putIfHasText(mapped, "operator", action.getOperator());
        putIfHasText(mapped, "field", action.getField());
        putIfNotNull(mapped, "fields", action.getFields());
        putIfNotNull(mapped, "targets", action.getTargets());
        return mapped;
    }

    private Map<String, Object> buildConfirmation(ConfirmationProperties confirmationSchema, String language) {
        Map<String, Object> confirmation = new LinkedHashMap<>();
        if (confirmationSchema == null) {
            return confirmation;
        }
        if (confirmationSchema.getMessage() != null && !confirmationSchema.getMessage().isEmpty()) {
            confirmation.put("message", resolveLabel(confirmationSchema.getMessage(), language));
        }
        return confirmation;
    }

    private Map<String, Object> buildActions(FormMetadataProperties formSchema, String language) {
        Map<String, Object> actions = new LinkedHashMap<>();
        if (formSchema == null || formSchema.getActions() == null) {
            return actions;
        }
        for (ActionProperties actionSchema : formSchema.getActions()) {
            if (actionSchema == null || !hasText(actionSchema.getName())) {
                continue;
            }
            Map<String, Object> action = new LinkedHashMap<>();
            action.put("enabled", Boolean.TRUE);
            action.put("label", resolveLabel(actionSchema.getLabel(), language));
            actions.put(actionSchema.getName(), action);
        }
        return actions;
    }

    private Object resolveValue(Map<String, Object> apimData, String dataKey) {
        if (apimData == null || !apimData.containsKey(dataKey)) {
            return "";
        }
        Object value = apimData.get(dataKey);
        return value != null ? value : "";
    }

    private boolean isReadonly(String configValue) {
        return "READONLY".equalsIgnoreCase(configValue);
    }

    private boolean isRequired(String configValue) {
        return "EDITABLE_COM".equalsIgnoreCase(configValue);
    }

    private String resolveLabel(Map<String, String> labels, String language) {
        if (labels == null || labels.isEmpty()) {
            return "";
        }
        String label = labels.get(language);
        if (label == null && ZH_HK_LANGUAGE.equals(language)) {
            label = labels.get("zh-HK");
        }
        if (label == null) {
            label = labels.get(DEFAULT_LANGUAGE);
        }
        if (label == null) {
            label = labels.values().iterator().next();
        }
        return label != null ? label : "";
    }

    private void putIfHasText(Map<String, Object> target, String key, String value) {
        if (hasText(value)) {
            target.put(key, value);
        }
    }

    private void putIfNotNull(Map<String, Object> target, String key, Object value) {
        if (value != null) {
            target.put(key, value);
        }
    }

    private void logMissingYamlBinding(String configKey, String configValue) {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("item-id", configKey);
        event.put("function", "PersonalInformationWebMapper");
        event.put("sch-type", "personalInformation");
        event.put("config-value", configValue);
        log.error("Missing YAML apimBinding for APIM config item: {}", loggingSanitizer.toSafeString(event));
    }

    private void logUnknownConfigItemType(String configKey, String configValue, MemberInfoConfigItem configItem) {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("item-id", configKey);
        event.put("function", configItem != null ? configItem.function() : "PersonalInformationWebMapper");
        event.put("sch-type", configItem != null ? configItem.schType() : "personalInformation");
        event.put("config-value", configValue);
        event.put("item-type", configItem != null ? configItem.itemType() : null);
        log.error("Unknown APIM config item type: {}", loggingSanitizer.toSafeString(event));
    }

    private void logDuplicateYamlBinding(String configKey, String firstFieldId, String duplicateFieldId) {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("item-id", configKey);
        event.put("first-field", firstFieldId);
        event.put("duplicate-field", duplicateFieldId);
        event.put("function", "PersonalInformationWebMapper");
        event.put("sch-type", "personalInformation");
        log.error("Duplicate YAML apimBinding config item: {}", loggingSanitizer.toSafeString(event));
    }

    private void logMissingExpectedApimConfig(
            Map<String, BoundField> fieldsByConfigKey,
            Set<String> seenApimConfigItems) {
        for (Map.Entry<String, BoundField> expected : fieldsByConfigKey.entrySet()) {
            if (!seenApimConfigItems.contains(expected.getKey())) {
                Map<String, Object> event = new LinkedHashMap<>();
                event.put("expected-item-id", expected.getKey());
                event.put("mapped-field", expected.getValue().field().getId());
                log.error("APIM config missing expected item: {}", loggingSanitizer.toSafeString(event));
            }
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private record FieldLocation(String sectionId, FieldProperties field) {
    }

    private record BoundField(FieldProperties field, FieldLocation location, String dataKey) {
    }

    private record FieldState(String itemId, String configValue, Object value, FieldLocation location) {
    }
}