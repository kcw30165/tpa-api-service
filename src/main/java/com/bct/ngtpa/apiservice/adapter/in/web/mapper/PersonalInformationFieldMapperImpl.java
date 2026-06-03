package com.bct.ngtpa.apiservice.adapter.in.web.mapper;

import com.bct.ngtpa.apiservice.adapter.in.web.pageconfig.ActionProperties;
import com.bct.ngtpa.apiservice.adapter.in.web.pageconfig.BffPagesProperties;
import com.bct.ngtpa.apiservice.adapter.in.web.pageconfig.ConfirmationProperties;
import com.bct.ngtpa.apiservice.adapter.in.web.pageconfig.FieldProperties;
import com.bct.ngtpa.apiservice.adapter.in.web.pageconfig.FormMetadataProperties;
import com.bct.ngtpa.apiservice.adapter.in.web.pageconfig.PageMetadataProperties;
import com.bct.ngtpa.apiservice.adapter.in.web.pageconfig.PageSchemaProperties;
import com.bct.ngtpa.apiservice.adapter.in.web.pageconfig.SectionProperties;
import com.bct.ngtpa.apiservice.infrastructure.logging.LoggingSanitizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class PersonalInformationFieldMapperImpl implements PersonalInformationFieldMapper {

    private static final Logger log = LoggerFactory.getLogger(PersonalInformationFieldMapperImpl.class);

    private static final String PAGE_KEY = "personalInformation";
    private static final String DEFAULT_LANGUAGE = "en";
    private static final String ZH_HK_LANGUAGE = "zh_HK";

    private final LoggingSanitizer loggingSanitizer;

    // APIM item-id -> BFF field id mapping (Personal Information specific)
    private static final LinkedHashMap<String, String> APIM_TO_BFF = new LinkedHashMap<>();

    static {
        APIM_TO_BFF.put("ui-check-box-apply-all-member", "applyToAllMemberAccounts");
        APIM_TO_BFF.put("ui-check-box-of-same-as-residential-address", "mailingAddressSameAsResidential");
        APIM_TO_BFF.put("addr1", "residentialAddressLine1");
        APIM_TO_BFF.put("addr2", "residentialAddressLine2");
        APIM_TO_BFF.put("addr3", "residentialAddressLine3");
        APIM_TO_BFF.put("country", "residentialCountry");
        APIM_TO_BFF.put("corr-addr1", "mailingAddressLine1");
        APIM_TO_BFF.put("corr-addr2", "mailingAddressLine2");
        APIM_TO_BFF.put("corr-addr3", "mailingAddressLine3");
        APIM_TO_BFF.put("corr-country", "mailingCountry");
        APIM_TO_BFF.put("business-phone", "hongKongBusinessPhone");
        APIM_TO_BFF.put("business-phone-ext", "hongKongBusinessPhoneExtension");
        APIM_TO_BFF.put("mobile-number", "hongKongMobilePhone");
        APIM_TO_BFF.put("home-phone", "homeTel");
        APIM_TO_BFF.put("fax", "faxNo");
        APIM_TO_BFF.put("other-phone", "overseasPhoneNumber");
        APIM_TO_BFF.put("other-phone-area", "overseasAreaCode");
        APIM_TO_BFF.put("other-phone-country", "overseasCountryCode");
        APIM_TO_BFF.put("other-phone-ext", "overseasPhoneExtension");
        APIM_TO_BFF.put("email", "emailAddress");
        APIM_TO_BFF.put("sms-language", "smsLanguage");
        APIM_TO_BFF.put("ui-important-notes-section", "importantNotes");
    }

    public PersonalInformationFieldMapperImpl(LoggingSanitizer loggingSanitizer) {
        this.loggingSanitizer = loggingSanitizer;
    }

    @Override
    public Map<String, Object> map(Map<String, Object> apimData,
                                   Map<String, String> apimConfig,
                                   BffPagesProperties bffPagesProperties,
                                   String language) {

        PageSchemaProperties pageSchema = resolvePageSchema(bffPagesProperties);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("page", buildPage(pageSchema, language));
        response.put("form", buildForm(pageSchema, apimData, apimConfig, language));
        return response;
    }

    private PageSchemaProperties resolvePageSchema(BffPagesProperties bffPagesProperties) {
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
                                          String language) {
        Map<String, Object> form = new LinkedHashMap<>();
        FormMetadataProperties formSchema = pageSchema != null ? pageSchema.getForm() : null;
        PageMetadataProperties metadata = pageSchema != null ? pageSchema.getMetadata() : null;

        form.put("id", formSchema != null ? formSchema.getId() : null);
        form.put("version", metadata != null ? metadata.getVersion() : null);
        form.put("mode", formSchema != null && hasText(formSchema.getDefaultMode())
                ? formSchema.getDefaultMode()
                : "view");
        form.put("sections", buildSections(pageSchema, apimData, apimConfig, language));
        form.put("validationRules", pageSchema != null && pageSchema.getValidations() != null
                ? pageSchema.getValidations()
                : List.of());
        form.put("confirmation", buildConfirmation(pageSchema != null ? pageSchema.getConfirmation() : null, language));
        form.put("actions", buildActions(formSchema, language));

        return form;
    }

    private List<Map<String, Object>> buildSections(PageSchemaProperties pageSchema,
                                                    Map<String, Object> apimData,
                                                    Map<String, String> apimConfig,
                                                    String language) {
        if (pageSchema == null || pageSchema.getForm() == null || pageSchema.getForm().getSections() == null) {
            return List.of();
        }

        Map<String, FieldState> fieldStates = buildFieldStates(pageSchema, apimData, apimConfig, language);
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
            section.put("fields", buildSectionFields(sectionSchema, fieldStates, language));
            sections.add(section);
            defaultSectionOrder += 10;
        }

        return sections;
    }

    private Map<String, FieldState> buildFieldStates(PageSchemaProperties pageSchema,
                                                     Map<String, Object> apimData,
                                                     Map<String, String> apimConfig,
                                                     String language) {
        Map<String, FieldState> fieldStates = new LinkedHashMap<>();
        Set<String> seenApimItems = new HashSet<>();

        if (apimConfig == null || apimConfig.isEmpty()) {
            return fieldStates;
        }

        for (Map.Entry<String, String> cfg : apimConfig.entrySet()) {
            String itemId = cfg.getKey();
            String configValue = cfg.getValue();
            seenApimItems.add(itemId);

            String bffFieldId = APIM_TO_BFF.get(itemId);
            if (bffFieldId == null) {
                logMissingJavaMapping(itemId, configValue);
                continue;
            }

            if ("HIDDEN".equalsIgnoreCase(configValue)) {
                continue;
            }

            FieldLocation location = findFieldLocation(pageSchema, bffFieldId);
            if (location == null || location.field == null) {
                logMissingYamlField(itemId, bffFieldId, configValue);
                continue;
            }

            Object value = resolveValue(apimData, itemId);
            fieldStates.put(bffFieldId, new FieldState(itemId, configValue, value, location));
        }

        logMissingExpectedApimConfig(seenApimItems);
        return fieldStates;
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
            field.put("value", state.value());
            field.put("originalValue", state.value());
            field.put("readonly", isReadonly(state.configValue()));
            field.put("required", isRequired(state.configValue()));
            if (hasText(fieldSchema.getOptionSource())) {
                field.put("optionSource", fieldSchema.getOptionSource());
            }
            field.put("displayOrder", fieldSchema.getDisplayOrder() != null
                    ? fieldSchema.getDisplayOrder()
                    : defaultFieldOrder);
            if (fieldSchema.getValidations() != null && !fieldSchema.getValidations().isEmpty()) {
                field.put("validations", fieldSchema.getValidations());
            }

            fields.add(field);
            defaultFieldOrder += 10;
        }

        return fields;
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

    private Object resolveValue(Map<String, Object> apimData, String itemId) {
        if (apimData == null || !apimData.containsKey(itemId)) {
            return "";
        }
        Object value = apimData.get(itemId);
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

    private void logMissingJavaMapping(String itemId, String configValue) {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("item-id", itemId);
        event.put("function", "PersonalInformationFieldMapper");
        event.put("sch-type", "personalInformation");
        event.put("config-value", configValue);
        log.error("Missing Java mapping for APIM item: {}", loggingSanitizer.toSafeString(event));
    }

    private void logMissingYamlField(String itemId, String bffFieldId, String configValue) {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("item-id", itemId);
        event.put("mapped-field", bffFieldId);
        event.put("function", "PersonalInformationFieldMapper");
        event.put("sch-type", "personalInformation");
        event.put("config-value", configValue);
        log.error("BFF page YAML missing mapped field: {}", loggingSanitizer.toSafeString(event));
    }

    private void logMissingExpectedApimConfig(Set<String> seenApimItems) {
        for (String expectedApimItem : APIM_TO_BFF.keySet()) {
            if (!seenApimItems.contains(expectedApimItem)) {
                Map<String, Object> event = new LinkedHashMap<>();
                event.put("expected-item-id", expectedApimItem);
                event.put("mapped-field", APIM_TO_BFF.get(expectedApimItem));
                log.error("APIM config missing expected item: {}", loggingSanitizer.toSafeString(event));
            }
        }
    }

    private FieldLocation findFieldLocation(PageSchemaProperties page, String fieldId) {
        if (page == null || page.getForm() == null || page.getForm().getSections() == null) {
            return null;
        }
        for (SectionProperties section : page.getForm().getSections()) {
            if (section == null || section.getFields() == null) {
                continue;
            }
            for (FieldProperties field : section.getFields()) {
                if (field != null && fieldId.equals(field.getId())) {
                    return new FieldLocation(section.getId(), field);
                }
            }
        }
        return null;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private record FieldLocation(String sectionId, FieldProperties field) {
    }

    private record FieldState(String itemId, String configValue, Object value, FieldLocation location) {
    }
}
