package com.bct.ngtpa.apiservice.adapter.in.web.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PersonalInformationFeValidationResponseNormalizationRegressionTest {

    private final YamlResponseMapper mapper = new YamlResponseMapper(new ObjectMapper());

    @Test
    void recursivelyHidesRawConfigKeysInsideNestedValidationRules() {
        Map<String, Object> nestedRule = new LinkedHashMap<>();
        nestedRule.put("id", "nested.rule");
        nestedRule.put("type", "minLength");
        nestedRule.put("messageCode", "personalInformation.nested.message");
        nestedRule.put("labelCode", "personalInformation.nested.label");
        nestedRule.put("placeholderCode", "personalInformation.nested.placeholder");
        nestedRule.put("apimBinding", Map.of("data", "internal-data-key"));

        Map<String, Object> parent = new LinkedHashMap<>();
        parent.put("id", "parent.rule");
        parent.put("children", List.of(nestedRule));

        Map<String, Object> response = mapper.toResponseMap(parent, "en");

        assertThat(response).doesNotContainKeys("messageCode", "labelCode", "placeholderCode", "apimBinding");
        List<Map<String, Object>> children = listOfMaps(response.get("children"));
        assertThat(children).hasSize(1);
        assertThat(children.getFirst())
                .containsEntry("id", "nested.rule")
                .containsEntry("type", "minLength")
                .doesNotContainKeys("messageCode", "labelCode", "placeholderCode", "apimBinding");
    }

    @Test
    void recursivelyNormalizesIndexedMapsInsideLists() {
        Map<String, Object> indexedFields = new LinkedHashMap<>();
        indexedFields.put("0", "residentialAddressLine1");
        indexedFields.put("1", "residentialAddressLine2");

        Map<String, Object> group = new LinkedHashMap<>();
        group.put("name", "residentialAddress");
        group.put("fields", indexedFields);

        Map<String, Object> rule = new LinkedHashMap<>();
        rule.put("id", "address.atLeastOneRequired");
        rule.put("groups", List.of(group));

        Map<String, Object> response = mapper.toResponseMap(rule, "en");
        List<Map<String, Object>> groups = listOfMaps(response.get("groups"));

        assertThat(groups.getFirst().get("fields"))
                .isEqualTo(List.of("residentialAddressLine1", "residentialAddressLine2"));
    }

    @Test
    void nonIndexedMapsRemainObjects() {
        Map<String, Object> nonIndexed = new LinkedHashMap<>();
        nonIndexed.put("operator", "required");
        nonIndexed.put("field", "hongKongBusinessPhone");

        Map<String, Object> rule = new LinkedHashMap<>();
        rule.put("id", "businessPhone.requiredWhenExtensionPresent");
        rule.put("then", nonIndexed);

        Map<String, Object> response = mapper.toResponseMap(rule, "en");

        assertThat(response.get("then"))
                .isInstanceOf(Map.class)
                .isEqualTo(Map.of("operator", "required", "field", "hongKongBusinessPhone"));
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> listOfMaps(Object value) {
        assertThat(value).isInstanceOf(List.class);
        return (List<Map<String, Object>>) value;
    }
}
