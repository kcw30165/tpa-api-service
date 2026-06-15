package com.bct.ngtpa.apiservice.adapter.in.web.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class PersonalInformationGetValidationResponseBoundaryGuardTest {

    private static final Set<String> RAW_KEYS = Set.of("messageCode", "labelCode", "placeholderCode", "apimBinding");

    private final YamlResponseMapper mapper = new YamlResponseMapper(new ObjectMapper());

    @Test
    void representativeFormPayloadDoesNotLeakRawValidationConfigAndUsesArrayFields() {
        Map<String, Object> fieldValidation = new LinkedHashMap<>();
        fieldValidation.put("id", "hongKongBusinessPhone.minLength");
        fieldValidation.put("type", "minLength");
        fieldValidation.put("value", 8);
        fieldValidation.put("code", "personalInformation.businessPhone.invalid");
        fieldValidation.put("message", "Please input a valid Hong Kong Business Telephone Number.");
        fieldValidation.put("messageCode", "personalInformation.businessPhone.invalid.message");

        Map<String, Object> field = new LinkedHashMap<>();
        field.put("name", "hongKongBusinessPhone");
        field.put("label", "Hong Kong Business Telephone Number");
        field.put("labelCode", "personalInformation.field.hongKongBusinessPhone.label");
        field.put("placeholderCode", "personalInformation.field.hongKongBusinessPhone.placeholder");
        field.put("apimBinding", Map.of("data", "business-phone", "config", "business-phone"));
        field.put("validations", List.of(fieldValidation));

        Map<String, Object> section = new LinkedHashMap<>();
        section.put("id", "localContactNumber");
        section.put("fields", List.of(field));

        Map<String, Object> indexedResidentialFields = new LinkedHashMap<>();
        indexedResidentialFields.put("0", "residentialAddressLine1");
        indexedResidentialFields.put("1", "residentialAddressLine2");
        indexedResidentialFields.put("2", "residentialAddressLine3");
        indexedResidentialFields.put("3", "residentialCountry");

        Map<String, Object> group = new LinkedHashMap<>();
        group.put("name", "residentialAddress");
        group.put("fields", indexedResidentialFields);

        Map<String, Object> when = new LinkedHashMap<>();
        when.put("operator", "allGroupsEmpty");
        when.put("groups", List.of(group));

        Map<String, Object> pageRule = new LinkedHashMap<>();
        pageRule.put("id", "address.atLeastOneRequired");
        pageRule.put("type", "groupRequirement");
        pageRule.put("when", when);
        pageRule.put("message", "Please input a residential address or correspondence address.");
        pageRule.put("messageCode", "personalInformation.address.atLeastOneRequired.message");

        Map<String, Object> form = new LinkedHashMap<>();
        form.put("id", "personalInformationForm");
        form.put("sections", List.of(section));
        form.put("validationRules", List.of(pageRule));

        Map<String, Object> response = mapper.toResponseMap(form, "en");

        assertNoRawKeys(response);

        List<Map<String, Object>> validationRules = listOfMaps(response.get("validationRules"));
        Map<String, Object> responseWhen = map(validationRules.getFirst().get("when"));
        List<Map<String, Object>> responseGroups = listOfMaps(responseWhen.get("groups"));
        assertThat(responseGroups.getFirst().get("fields"))
                .isEqualTo(List.of(
                        "residentialAddressLine1",
                        "residentialAddressLine2",
                        "residentialAddressLine3",
                        "residentialCountry"));
    }

    @Test
    void mapperSourceKeepsFeBoundaryDenyListCentralized() throws Exception {
        String source = java.nio.file.Files.readString(java.nio.file.Path.of(
                "src/main/java/com/bct/ngtpa/apiservice/adapter/in/web/mapper/YamlResponseMapper.java"));

        assertThat(source)
                .contains("isHiddenResponseKey")
                .contains("messageCode")
                .contains("labelCode")
                .contains("placeholderCode")
                .contains("apimBinding")
                .contains("isNumericIndexedMap")
                .contains("normalizeResponseValue");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> map(Object value) {
        assertThat(value).isInstanceOf(Map.class);
        return (Map<String, Object>) value;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> listOfMaps(Object value) {
        assertThat(value).isInstanceOf(List.class);
        return (List<Map<String, Object>>) value;
    }

    private void assertNoRawKeys(Object value) {
        if (value instanceof Map<?, ?> map) {
            assertThat(map.keySet().stream().map(Object::toString).toList()).doesNotContainAnyElementsOf(RAW_KEYS);
            map.values().forEach(this::assertNoRawKeys);
            return;
        }
        if (value instanceof List<?> list) {
            list.forEach(this::assertNoRawKeys);
        }
    }
}
