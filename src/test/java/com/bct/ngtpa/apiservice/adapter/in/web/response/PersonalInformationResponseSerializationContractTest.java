package com.bct.ngtpa.apiservice.adapter.in.web.response;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PersonalInformationResponseSerializationContractTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void updateMutationSerializesResultAsAccountArrayNotWrapperObject() throws Exception {
        ApiError emailRequired = ApiError.field(
                "personalInformation.email.required",
                "Email is required.",
                List.of("emailAddress"),
                "SERVER");
        MutationResponse<List<PersonalInformationUpdateResultResponse>> response = MutationResponse.success(
                ApiStatus.PARTIAL_SUCCESS,
                List.of(
                        new PersonalInformationUpdateResultResponse(
                                true,
                                true,
                                "00000000118",
                                "2",
                                "DB",
                                "260001373",
                                "2025-12-31",
                                "15:42:52",
                                List.of()),
                        new PersonalInformationUpdateResultResponse(
                                false,
                                false,
                                "00000000118",
                                "3",
                                "DB",
                                "260001374",
                                "2025-12-31",
                                "15:42:52",
                                List.of(emailRequired))),
                List.of(ApiMessage.warning(
                        "personalInformation.update.partialSuccess",
                        "The update was successful for the selected account, but failed for policy 00000000118 certificate 3.",
                        "PAGE")));

        JsonNode json = objectMapper.readTree(objectMapper.writeValueAsString(response));

        assertThat(json.path("success").booleanValue()).isTrue();
        assertThat(json.path("status").asText()).isEqualTo("PARTIAL_SUCCESS");
        assertThat(json.path("result").isArray()).isTrue();
        assertThat(json.path("result")).hasSize(2);
        assertThat(json.path("result").get(0).path("selected").booleanValue()).isTrue();
        assertThat(json.path("result").get(0).path("success").booleanValue()).isTrue();
        assertThat(json.path("result").get(0).path("policyNo").asText()).isEqualTo("00000000118");
        assertThat(json.path("result").get(0).path("certNo").asText()).isEqualTo("2");
        assertThat(json.path("result").get(0).path("errors").isArray()).isTrue();
        assertThat(json.path("result").get(0).path("errors")).isEmpty();
        assertThat(json.path("result").get(1).path("errors")).hasSize(1);
        assertThat(json.path("result").has("accounts")).isFalse();
        assertThat(json.path("result").has("refNo")).isFalse();
        assertThat(json.path("errors")).isEmpty();
    }

    @Test
    void selectedFailureMutationSerializesAccountArrayAndSelectedTopLevelErrors() throws Exception {
        ApiError selectedError = ApiError.field(
                "personalInformation.email.required",
                "Email is required.",
                List.of("emailAddress"),
                "SERVER");
        MutationResponse<List<PersonalInformationUpdateResultResponse>> response = MutationResponse.failure(
                ApiStatus.PARTIAL_SUCCESS,
                List.of(
                        new PersonalInformationUpdateResultResponse(
                                false,
                                true,
                                "00000000118",
                                "2",
                                "DB",
                                "260001373",
                                "2025-12-31",
                                "15:42:52",
                                List.of()),
                        new PersonalInformationUpdateResultResponse(
                                true,
                                false,
                                "00000000118",
                                "3",
                                "DB",
                                "260001374",
                                "2025-12-31",
                                "15:42:52",
                                List.of(selectedError))),
                List.of(selectedError));

        JsonNode json = objectMapper.readTree(objectMapper.writeValueAsString(response));

        assertThat(json.path("success").booleanValue()).isFalse();
        assertThat(json.path("status").asText()).isEqualTo("PARTIAL_SUCCESS");
        assertThat(json.path("result").isArray()).isTrue();
        assertThat(json.path("result")).hasSize(2);
        assertThat(json.path("result").get(1).path("selected").booleanValue()).isTrue();
        assertThat(json.path("result").get(1).path("success").booleanValue()).isFalse();
        assertThat(json.path("result").get(1).path("errors")).hasSize(1);
        assertThat(json.path("errors")).hasSize(1);
        assertThat(json.path("errors").get(0).path("targets").get(0).asText()).isEqualTo("emailAddress");
        assertThat(json.path("messages")).isEmpty();
    }

    @Test
    void formSchemaSerializesOptionSetsForSmsLanguages() throws Exception {
        FormSchemaResponse form = new FormSchemaResponse(
                "personalInformationForm",
                "1.0",
                "view",
                List.of(),
                Map.of("smsLanguages", List.of(
                        new FormOptionResponse("en", "English"),
                        new FormOptionResponse("zh_HK", "Traditional Chinese"))),
                List.of(),
                Map.of(),
                Map.of());

        JsonNode json = objectMapper.readTree(objectMapper.writeValueAsString(form));

        assertThat(json.path("optionSets").has("smsLanguages")).isTrue();
        assertThat(json.path("optionSets").path("smsLanguages").isArray()).isTrue();
        assertThat(json.path("optionSets").path("smsLanguages")).hasSize(2);
        assertThat(json.path("optionSets").path("smsLanguages").get(0).path("value").asText()).isEqualTo("en");
        assertThat(json.path("optionSets").path("smsLanguages").get(0).path("text").asText()).isEqualTo("English");
        assertThat(json.path("optionSets").path("smsLanguages").get(1).path("value").asText()).isEqualTo("zh_HK");
        assertThat(json.path("optionSets").path("smsLanguages").get(1).path("text").asText()).isEqualTo("Traditional Chinese");
    }
}
