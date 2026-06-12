package com.bct.ngtpa.apiservice.adapter.in.web.mapper;

import com.bct.ngtpa.apiservice.shared.config.ConfigVariantCandidateGenerator;

import com.bct.ngtpa.apiservice.adapter.in.web.pageconfig.ActionProperties;
import com.bct.ngtpa.apiservice.adapter.in.web.pageconfig.ApimBindingProperties;
import com.bct.ngtpa.apiservice.adapter.in.web.pageconfig.BffPagesProperties;
import com.bct.ngtpa.apiservice.adapter.in.web.pageconfig.FieldProperties;
import com.bct.ngtpa.apiservice.adapter.in.web.pageconfig.FormMetadataProperties;
import com.bct.ngtpa.apiservice.adapter.in.web.pageconfig.PageMetadataProperties;
import com.bct.ngtpa.apiservice.adapter.in.web.pageconfig.PageSchemaProperties;
import com.bct.ngtpa.apiservice.adapter.in.web.pageconfig.SectionProperties;
import com.bct.ngtpa.apiservice.adapter.in.web.pageconfig.ValidationRuleProperties;
import com.bct.ngtpa.apiservice.adapter.in.web.response.FieldResponse;
import com.bct.ngtpa.apiservice.adapter.in.web.response.FormPageResponse;
import com.bct.ngtpa.apiservice.adapter.in.web.response.FormSchemaResponse;
import com.bct.ngtpa.apiservice.adapter.in.web.response.PageResponse;
import com.bct.ngtpa.apiservice.adapter.in.web.response.SectionResponse;
import com.bct.ngtpa.apiservice.adapter.in.web.response.ValidationRuleResponse;
import com.bct.ngtpa.apiservice.application.dto.MemberInfoConfigItem;
import com.bct.ngtpa.apiservice.application.dto.MemberInfoConfigItemType;
import com.bct.ngtpa.apiservice.application.dto.PersonalInformationResult;
import com.bct.ngtpa.apiservice.infrastructure.logging.LoggingSanitizer;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Autowired;
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
    private final PageDisplayTextResolver pageDisplayTextResolver = new PageDisplayTextResolver(new ConfigVariantCandidateGenerator());

    private static final String DEFAULT_ACCOUNT_ENV = null;
    private static final String DEFAULT_TRUST_CODE = null;
    private static final String DEFAULT_SCHEME_TYPE = null;

    private final String accountEnv = DEFAULT_ACCOUNT_ENV;
    private final String trustCode = DEFAULT_TRUST_CODE;
    private final String schemeType = DEFAULT_SCHEME_TYPE;


    private final LoggingSanitizer loggingSanitizer;
    private final BffPagesProperties bffPagesProperties;
    private final YamlResponseMapper yamlResponseMapper;
    private final ObjectMapper responseObjectMapper = new ObjectMapper();
    
    @Autowired
    public PersonalInformationWebMapper(
            LoggingSanitizer loggingSanitizer,
            BffPagesProperties bffPagesProperties,
            YamlResponseMapper yamlResponseMapper) {
        this.loggingSanitizer = loggingSanitizer;
        this.bffPagesProperties = bffPagesProperties;
        this.yamlResponseMapper = yamlResponseMapper;
    }

    PersonalInformationWebMapper(
            LoggingSanitizer loggingSanitizer,
            BffPagesProperties bffPagesProperties) {
        this(loggingSanitizer, bffPagesProperties, new YamlResponseMapper(new ObjectMapper()));
    }

    public FormPageResponse<FormSchemaResponse> toFormPageResponse(
            PersonalInformationResult result,
            String language) {
        return toFormPageResponse(result, language, null, null, null);
    }

    public FormPageResponse<FormSchemaResponse> toFormPageResponse(
            PersonalInformationResult result,
            String language,
            String accountEnv,
            String trustCode,
            String schemeType) {
        Map<String, Object> apimData = result != null ? result.data() : Map.of();
        Map<String, String> apimConfig = result != null ? result.config() : Map.of();
        Map<String, MemberInfoConfigItem> apimConfigItems = result != null ? result.configItems() : Map.of();
        return toFormPageResponse(apimData, apimConfig, apimConfigItems, language);
    }

    public FormPageResponse<FormSchemaResponse> toFormPageResponse(
            Map<String, Object> apimData,
            Map<String, String> apimConfig,
            String language) {
        return toFormPageResponse(apimData, apimConfig, Map.of(), language);
    }

    public FormPageResponse<FormSchemaResponse> toFormPageResponse(
            Map<String, Object> apimData,
            Map<String, String> apimConfig,
            Map<String, MemberInfoConfigItem> apimConfigItems,
            String language) {

        PageSchemaProperties pageSchema = resolvePageSchema();
        return FormPageResponse.success(
                buildPage(pageSchema, language, accountEnv, trustCode, schemeType),
                buildForm(pageSchema, apimData, apimConfig, apimConfigItems, language));
    }

    /**
     * Backward-compatible map view for existing mapper tests and callers that have not yet moved
     * to FormPageResponse<FormSchemaResponse>.
     */
    public Map<String, Object> toResponse(PersonalInformationResult result, String language) {
        return toMap(toFormPageResponse(result, language));
    }

    public Map<String, Object> toResponse(
            Map<String, Object> apimData,
            Map<String, String> apimConfig,
            String language) {
        return toMap(toFormPageResponse(apimData, apimConfig, language));
    }

    public Map<String, Object> toResponse(
            Map<String, Object> apimData,
            Map<String, String> apimConfig,
            Map<String, MemberInfoConfigItem> apimConfigItems,
            String language) {
        return toMap(toFormPageResponse(apimData, apimConfig, apimConfigItems, language));
    }

    private Map<String, Object> toMap(FormPageResponse<FormSchemaResponse> response) {
        return responseObjectMapper.convertValue(response, new TypeReference<>() {
        });
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

    private PageResponse buildPage(PageSchemaProperties pageSchema, String language, String accountEnv, String trustCode, String schemeType) {
        PageMetadataProperties metadata = pageSchema != null ? pageSchema.getMetadata() : null;
        return new PageResponse(
                metadata != null ? metadata.getId() : null,
                resolveLabel(metadata != null ? metadata.getTitle() : null, language),
                language);
    }

    private FormSchemaResponse buildForm(PageSchemaProperties pageSchema,
            Map<String, Object> apimData,
            Map<String, String> apimConfig,
            Map<String, MemberInfoConfigItem> apimConfigItems,
            String language) {
        FormMetadataProperties formSchema = pageSchema != null ? pageSchema.getForm() : null;
        PageMetadataProperties metadata = pageSchema != null ? pageSchema.getMetadata() : null;
        return new FormSchemaResponse(
                formSchema != null ? formSchema.getId() : null,
                metadata != null ? metadata.getVersion() : null,
                formSchema != null && hasText(formSchema.getDefaultMode()) ? formSchema.getDefaultMode() : "view",
                buildSections(pageSchema, apimData, apimConfig, apimConfigItems, language),
                mapValidationRules(pageSchema != null ? pageSchema.getValidations() : null, language),
                yamlResponseMapper.toResponseMap(pageSchema != null ? pageSchema.getConfirmation() : null, language),
                buildActions(formSchema, language));
    }

    private List<SectionResponse> buildSections(PageSchemaProperties pageSchema,
            Map<String, Object> apimData,
            Map<String, String> apimConfig,
            Map<String, MemberInfoConfigItem> apimConfigItems,
            String language) {
        if (pageSchema == null || pageSchema.getForm() == null || pageSchema.getForm().getSections() == null) {
            return List.of();
        }
        Map<String, FieldState> fieldStates = buildFieldStates(pageSchema, apimData, apimConfig, apimConfigItems);
        List<SectionResponse> sections = new ArrayList<>();
        int defaultSectionOrder = 10;
        for (SectionProperties sectionSchema : pageSchema.getForm().getSections()) {
            if (sectionSchema == null || !hasText(sectionSchema.getId())) {
                continue;
            }
            List<FieldResponse> fields = buildSectionFields(sectionSchema, fieldStates, language);
            if (fields.isEmpty()) {
                defaultSectionOrder += 10;
                continue;
            }
            sections.add(new SectionResponse(
                    sectionSchema.getId(),
                    resolvePageDisplayText(pageSchema, sectionSchema.getTitleCode(), sectionSchema.getTitle(), language, accountEnv, trustCode, schemeType),
                    defaultSectionOrder,
                    fields));
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

    private List<FieldResponse> buildSectionFields(SectionProperties sectionSchema,
            Map<String, FieldState> fieldStates,
            String language) {
        if (sectionSchema.getFields() == null) {
            return List.of();
        }
        List<FieldResponse> fields = new ArrayList<>();
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
            List<ValidationRuleResponse> validations = mapValidationRules(fieldSchema.getValidations(), language);
            fields.add(new FieldResponse(
                    fieldSchema.getId(),
                    resolveLabel(fieldSchema.getLabel(), language),
                    hasText(fieldSchema.getDataType()) ? fieldSchema.getDataType() : null,
                    hasText(fieldSchema.getControlType()) ? fieldSchema.getControlType() : null,
                    state.value(),
                    state.value(),
                    isReadonly(state.configValue()),
                    isRequired(state.configValue()),
                    fieldSchema.getMinLength(),
                    fieldSchema.getMaxLength(),
                    hasText(fieldSchema.getPattern()) ? fieldSchema.getPattern() : null,
                    hasText(resolveLabel(fieldSchema.getPlaceholder(), language))
                            ? resolveLabel(fieldSchema.getPlaceholder(), language)
                            : null,
                    hasText(fieldSchema.getOptionSource()) ? fieldSchema.getOptionSource() : null,
                    fieldSchema.getCopyWhenChecked(),
                    fieldSchema.getDisplayOrder() != null ? fieldSchema.getDisplayOrder() : defaultFieldOrder,
                    validations));
            defaultFieldOrder += 10;
        }
        return fields;
    }

    private List<ValidationRuleResponse> mapValidationRules(List<ValidationRuleProperties> rules, String language) {
        return yamlResponseMapper.toResponseList(rules, language).stream()
                .map(ValidationRuleResponse::from)
                .filter(java.util.Objects::nonNull)
                .toList();
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

    private String resolvePageDisplayText(
            PageSchemaProperties pageSchema,
            String code,
            Map<String, String> inlineFallback,
            String language,
            String accountEnv,
            String trustCode,
            String schemeType) {
        return pageDisplayTextResolver.resolve(
                pageSchema == null ? Map.of() : pageSchema.getDisplay(),
                code,
                inlineFallback,
                language,
                accountEnv,
                trustCode,
                schemeType);
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