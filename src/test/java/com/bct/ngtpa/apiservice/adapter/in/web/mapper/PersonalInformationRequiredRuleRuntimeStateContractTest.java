package com.bct.ngtpa.apiservice.adapter.in.web.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PersonalInformationRequiredRuleRuntimeStateContractTest {

    private final YamlResponseMapper mapper = new YamlResponseMapper(new ObjectMapper());

    @Test
    void yamlRequiredValidationMayBePresentWhenRuntimeFieldIsReadonlyAndNotRequired() {
        Map<String, Object> requiredRule = new LinkedHashMap<>();
        requiredRule.put("id", "email.required");
        requiredRule.put("type", "required");
        requiredRule.put("severity", "error");
        requiredRule.put("code", "personalInformation.email.required");
        requiredRule.put("message", "Please provide a valid email address.");
        requiredRule.put("messageCode", "personalInformation.email.required.message");

        Map<String, Object> field = new LinkedHashMap<>();
        field.put("name", "emailAddress");
        field.put("dataType", "string");
        field.put("controlType", "email");
        field.put("readonly", true);
        field.put("required", false);
        field.put("validations", List.of(requiredRule));

        Map<String, Object> response = mapper.toResponseMap(field, "en");

        assertThat(response)
                .containsEntry("readonly", true)
                .containsEntry("required", false);
        List<Map<String, Object>> validations = listOfMaps(response.get("validations"));
        assertThat(validations).hasSize(1);
        assertThat(validations.getFirst())
                .containsEntry("id", "email.required")
                .containsEntry("type", "required")
                .containsEntry("code", "personalInformation.email.required")
                .containsEntry("message", "Please provide a valid email address.")
                .doesNotContainKey("messageCode");
    }

    @Test
    void editableMandatoryStateIsRepresentedByReadonlyFalseAndRequiredTrue() {
        Map<String, Object> requiredRule = new LinkedHashMap<>();
        requiredRule.put("id", "email.required");
        requiredRule.put("type", "required");
        requiredRule.put("severity", "error");
        requiredRule.put("code", "personalInformation.email.required");
        requiredRule.put("message", "Please provide a valid email address.");

        Map<String, Object> field = new LinkedHashMap<>();
        field.put("name", "emailAddress");
        field.put("readonly", false);
        field.put("required", true);
        field.put("validations", List.of(requiredRule));

        Map<String, Object> response = mapper.toResponseMap(field, "en");

        assertThat(response)
                .containsEntry("readonly", false)
                .containsEntry("required", true);
        assertThat(listOfMaps(response.get("validations")).getFirst())
                .containsEntry("type", "required");
    }

    @Test
    void requiredRuntimeStateContractIsDocumented() throws Exception {
        Path contract = Path.of("docs/personal-information-get-validation-response-quality-contract.md");
        assertThat(contract).exists();
        String document = Files.readString(contract);

        assertThat(document)
                .contains("validations[] contains available rule definitions")
                .contains("required and readonly are runtime field state")
                .contains("EDITABLE_COM")
                .contains("READONLY")
                .contains("readonly=false")
                .contains("required=true")
                .contains("type: required may be present even when required=false")
                .contains("Do not expose raw APIM config-value to FE");
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> listOfMaps(Object value) {
        assertThat(value).isInstanceOf(List.class);
        return (List<Map<String, Object>>) value;
    }
}
