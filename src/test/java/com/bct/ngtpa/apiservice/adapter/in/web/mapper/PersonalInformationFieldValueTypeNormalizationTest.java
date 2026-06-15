package com.bct.ngtpa.apiservice.adapter.in.web.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PersonalInformationFieldValueTypeNormalizationTest {

    private final YamlResponseMapper mapper = new YamlResponseMapper(new ObjectMapper());

    @Test
    void booleanFieldEmptyStringValueIsNormalizedToFalse() {
        Map<String, Object> field = new LinkedHashMap<>();
        field.put("name", "mailingAddressSameAsResidential");
        field.put("dataType", "boolean");
        field.put("controlType", "checkbox");
        field.put("value", "");
        field.put("originalValue", "");

        Map<String, Object> response = mapper.toResponseMap(field, "en");

        assertThat(response.get("value"))
                .as("Boolean fields must not expose empty string values to FE")
                .isEqualTo(false);
        assertThat(response.get("originalValue"))
                .as("Boolean originalValue must be normalized consistently with value")
                .isEqualTo(false);
    }

    @Test
    void booleanFieldStringTrueAndFalseValuesAreNormalizedToBooleanValues() {
        Map<String, Object> trueField = new LinkedHashMap<>();
        trueField.put("name", "applyToAllMemberAccounts");
        trueField.put("dataType", "boolean");
        trueField.put("controlType", "checkbox");
        trueField.put("value", "true");
        trueField.put("originalValue", "false");

        Map<String, Object> trueResponse = mapper.toResponseMap(trueField, "en");

        assertThat(trueResponse.get("value")).isEqualTo(true);
        assertThat(trueResponse.get("originalValue")).isEqualTo(false);
    }

    @Test
    void arrayFieldEmptyStringValueIsNormalizedToEmptyList() {
        Map<String, Object> field = new LinkedHashMap<>();
        field.put("name", "importantNotes");
        field.put("dataType", "array");
        field.put("controlType", "noteList");
        field.put("value", "");
        field.put("originalValue", "");

        Map<String, Object> response = mapper.toResponseMap(field, "en");

        assertThat(response.get("value"))
                .as("Array fields must not expose empty string values to FE")
                .isEqualTo(List.of());
        assertThat(response.get("originalValue"))
                .as("Array originalValue must be normalized consistently with value")
                .isEqualTo(List.of());
    }

    @Test
    void stringFieldEmptyStringValueIsPreserved() {
        Map<String, Object> field = new LinkedHashMap<>();
        field.put("name", "emailAddress");
        field.put("dataType", "string");
        field.put("controlType", "email");
        field.put("value", "");
        field.put("originalValue", "");

        Map<String, Object> response = mapper.toResponseMap(field, "en");

        assertThat(response.get("value"))
                .as("String fields may still expose empty string values")
                .isEqualTo("");
        assertThat(response.get("originalValue"))
                .as("String originalValue should remain unchanged")
                .isEqualTo("");
    }
}
