package com.bct.ngtpa.apiservice.adapter.in.web.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PersonalInformationFeValidationResponseContractTest {

    private final YamlResponseMapper mapper = new YamlResponseMapper(new ObjectMapper());

    @Test
    void validationGroupFieldsAreReturnedAsArraysNotIndexedObjects() {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("0", "residentialAddressLine1");
        fields.put("1", "residentialAddressLine2");
        fields.put("2", "residentialAddressLine3");
        fields.put("3", "residentialCountry");

        Map<String, Object> group = new LinkedHashMap<>();
        group.put("name", "residentialAddress");
        group.put("fields", fields);

        Map<String, Object> when = new LinkedHashMap<>();
        when.put("operator", "allGroupsEmpty");
        when.put("groups", List.of(group));

        Map<String, Object> rule = new LinkedHashMap<>();
        rule.put("id", "address.atLeastOneRequired");
        rule.put("type", "groupRequirement");
        rule.put("when", when);
        rule.put("messageCode", "personalInformation.address.atLeastOneRequired.message");

        Map<String, Object> response = mapper.toResponseMap(rule, "en");

        Map<String, Object> responseWhen = map(response.get("when"));
        List<Map<String, Object>> responseGroups = listOfMaps(responseWhen.get("groups"));
        assertThat(responseGroups).hasSize(1);
        assertThat(responseGroups.getFirst().get("fields"))
                .as("FE validation contract must expose groups[].fields as JSON array")
                .isEqualTo(List.of(
                        "residentialAddressLine1",
                        "residentialAddressLine2",
                        "residentialAddressLine3",
                        "residentialCountry"));
    }

    @Test
    void rawDisplayCodeKeysAreNotReturnedInFeValidationRulePayloads() {
        Map<String, Object> rule = new LinkedHashMap<>();
        rule.put("id", "hongKongBusinessPhone.minLength");
        rule.put("type", "minLength");
        rule.put("value", 8);
        rule.put("code", "personalInformation.businessPhone.invalid");
        rule.put("messageCode", "personalInformation.businessPhone.invalid.message");
        rule.put("labelCode", "personalInformation.field.hongKongBusinessPhone.label");
        rule.put("placeholderCode", "personalInformation.field.hongKongBusinessPhone.placeholder");
        rule.put("apimBinding", Map.of("data", "business-phone", "config", "business-phone"));

        Map<String, Object> response = mapper.toResponseMap(rule, "en");

        assertThat(response)
                .containsEntry("id", "hongKongBusinessPhone.minLength")
                .containsEntry("type", "minLength")
                .containsEntry("value", 8)
                .containsEntry("code", "personalInformation.businessPhone.invalid")
                .doesNotContainKeys("messageCode", "labelCode", "placeholderCode", "apimBinding");
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
}
