package com.bct.ngtpa.apiservice.adapter.in.web.mapper;

import com.bct.ngtpa.apiservice.adapter.in.web.pageconfig.BffPagesProperties;
import com.bct.ngtpa.apiservice.adapter.in.web.pageconfig.FieldProperties;
import com.bct.ngtpa.apiservice.adapter.in.web.pageconfig.PageSchemaProperties;
import com.bct.ngtpa.apiservice.adapter.in.web.pageconfig.SectionProperties;
import com.bct.ngtpa.apiservice.infrastructure.logging.LoggingSanitizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class PersonalInformationFieldMapperImpl implements PersonalInformationFieldMapper {

    private static final Logger log = LoggerFactory.getLogger(PersonalInformationFieldMapperImpl.class);

    private final LoggingSanitizer loggingSanitizer;

    // APIM item-id -> BFF field id mapping (Personal Information specific)
    private static final LinkedHashMap<String, String> APIM_TO_BFF = new LinkedHashMap<>();

    static {
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
    }

    public PersonalInformationFieldMapperImpl(LoggingSanitizer loggingSanitizer) {
        this.loggingSanitizer = loggingSanitizer;
    }

    @Override
    public Map<String, Object> map(Map<String, Object> apimData,
                                   Map<String, String> apimConfig,
                                   BffPagesProperties bffPagesProperties) {

        Map<String, Object> result = new LinkedHashMap<>();
        Map<String, Map<String, Object>> emittedFields = new LinkedHashMap<>();

        PageSchemaProperties page = bffPagesProperties.getPages().get("personalInformation");
        if (page == null) {
            log.error("personalInformation page schema not found");
            result.put("fields", emittedFields);
            return result;
        }

        Set<String> seenApimItems = new HashSet<>();

        if (apimConfig != null) {
            for (Map.Entry<String, String> cfg : apimConfig.entrySet()) {
                String itemId = cfg.getKey();
                String configValue = cfg.getValue();
                seenApimItems.add(itemId);

                String bffFieldId = APIM_TO_BFF.get(itemId);
                if (bffFieldId == null) {
                    // Log missing Java mapping (sanitize values)
                    Map<String, Object> event = Map.of(
                            "item-id", itemId,
                            "function", "PersonalInformationFieldMapper",
                            "sch-type", "personalInformation",
                            "config-value", configValue
                    );
                    log.error("Missing Java mapping for APIM item: {}",
                            loggingSanitizer.toSafeString(event));
                    continue;
                }

                if ("HIDDEN".equalsIgnoreCase(configValue)) {
                    // omit
                    continue;
                }

                Map<String, Object> fieldMeta = new LinkedHashMap<>();
                fieldMeta.put("id", bffFieldId);

                // resolve field properties from page schema
                FieldLocation loc = findFieldLocation(page, bffFieldId);
                if (loc != null && loc.field != null) {
                    FieldProperties fp = loc.field;
                    fieldMeta.put("label", fp.getLabel());
                    fieldMeta.put("optionSource", fp.getOptionSource());
                    fieldMeta.put("validations", fp.getValidations());
                    fieldMeta.put("section", loc.sectionId);
                }

                // value resolution
                Object value = apimData != null && apimData.containsKey(itemId) ? apimData.get(itemId) : null;
                fieldMeta.put("value", value);
                fieldMeta.put("originalValue", value);

                // readonly/required per config-value
                switch (configValue == null ? "" : configValue) {
                    case "READONLY":
                        fieldMeta.put("readonly", Boolean.TRUE);
                        fieldMeta.put("required", Boolean.FALSE);
                        break;
                    case "EDITABLE_COM":
                        fieldMeta.put("readonly", Boolean.FALSE);
                        fieldMeta.put("required", Boolean.TRUE);
                        break;
                    case "EDITABLE_OPTION":
                        fieldMeta.put("readonly", Boolean.FALSE);
                        fieldMeta.put("required", Boolean.FALSE);
                        break;
                    default:
                        fieldMeta.put("readonly", Boolean.FALSE);
                        fieldMeta.put("required", Boolean.FALSE);
                        break;
                }

                emittedFields.put(bffFieldId, fieldMeta);
            }
        }

        // Detect Java mappings whose APIM item-id was not returned: log error and do not emit
        for (String expectedApimItem : APIM_TO_BFF.keySet()) {
            if (!seenApimItems.contains(expectedApimItem)) {
                String mappedField = APIM_TO_BFF.get(expectedApimItem);
                Map<String, Object> event = Map.of(
                        "expected-item-id", expectedApimItem,
                        "mapped-field", mappedField
                );
                log.error("APIM config missing expected item: {}",
                        loggingSanitizer.toSafeString(event));
            }
        }

        // Group emitted fields into sections following page schema
        Map<String, List<String>> sections = new LinkedHashMap<>();
        if (page.getForm() != null && page.getForm().getSections() != null) {
            for (SectionProperties sec : page.getForm().getSections()) {
                List<String> ids = new ArrayList<>();
                if (sec.getFields() != null) {
                    for (FieldProperties fp : sec.getFields()) {
                        if (fp != null && fp.getId() != null && emittedFields.containsKey(fp.getId())) {
                            ids.add(fp.getId());
                        }
                    }
                }
                sections.put(sec.getId(), ids);
            }
        }

        // Include confirmation metadata
        result.put("fields", emittedFields);
        result.put("sections", sections);
        result.put("confirmation", page.getConfirmation());

        return result;
    }

    private static class FieldLocation {
        final String sectionId;
        final FieldProperties field;

        FieldLocation(String sectionId, FieldProperties field) {
            this.sectionId = sectionId;
            this.field = field;
        }
    }

    private FieldLocation findFieldLocation(PageSchemaProperties page, String fieldId) {
        if (page == null || page.getForm() == null || page.getForm().getSections() == null) return null;
        for (SectionProperties sec : page.getForm().getSections()) {
            if (sec.getFields() == null) continue;
            for (FieldProperties fp : sec.getFields()) {
                if (fp != null && fieldId.equals(fp.getId())) {
                    return new FieldLocation(sec.getId(), fp);
                }
            }
        }
        return null;
    }
}
