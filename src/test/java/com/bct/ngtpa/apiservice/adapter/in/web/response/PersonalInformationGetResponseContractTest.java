package com.bct.ngtpa.apiservice.adapter.in.web.response;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;

class PersonalInformationGetResponseContractTest {

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  void getResponseSerializesPageAndFormUnderResultData() throws Exception {
    MutationResponse<PersonalInformationResultResponse> response = response();

    JsonNode json = objectMapper.readTree(objectMapper.writeValueAsString(response));

    assertThat(json.path("success").booleanValue()).isTrue();
    assertThat(json.path("status").asText()).isEqualTo("SUCCESS");
    assertThat(json.path("result").path("data").path("page").path("id").asText())
        .isEqualTo("personalInformationPage");
    assertThat(json.path("result").path("data").path("page").path("lang").asText())
        .isEqualTo("en");
    assertThat(json.path("result").path("data").path("form").path("id").asText())
        .isEqualTo("personalInformationForm");
    assertThat(json.path("messages").isArray()).isTrue();
    assertThat(json.path("messages")).isEmpty();
    assertThat(json.path("errors").isArray()).isTrue();
    assertThat(json.path("errors")).isEmpty();
  }

  @Test
  void getResponseKeepsPageAndFormOutOfTopLevelAndResult() throws Exception {
    MutationResponse<PersonalInformationResultResponse> response = response();

    JsonNode json = objectMapper.readTree(objectMapper.writeValueAsString(response));

    assertThat(json.has("page")).isFalse();
    assertThat(json.has("form")).isFalse();
    assertThat(json.path("result").has("page")).isFalse();
    assertThat(json.path("result").has("form")).isFalse();
    assertThat(json.path("result").has("data")).isTrue();
  }

  private static MutationResponse<PersonalInformationResultResponse> response() {
    FormPageResponse<FormSchemaResponse> formPage =
        FormPageResponse.success(
            new PageResponse("personalInformationPage", "Personal Information", "en"),
            new FormSchemaResponse("personalInformationForm", "1.0", "view", null, null, null, null));
    return MutationResponse.success(
        ApiStatus.SUCCESS, PersonalInformationResultResponse.from(formPage), List.of());
  }
}
