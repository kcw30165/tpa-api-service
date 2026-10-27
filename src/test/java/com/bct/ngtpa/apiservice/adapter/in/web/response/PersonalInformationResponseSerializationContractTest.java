package com.bct.ngtpa.apiservice.adapter.in.web.response;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PersonalInformationResponseSerializationContractTest {
  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  void partialSuccessSerializesOnlyApprovedFieldsInDataMessagesAndErrors() throws Exception {
    var error =
        new MappedError("FIELD", "personalInformation.field.required", List.of("emailAddress"));
    var response =
        new UpdatePersonalInformationResponse(
            true,
            ApiStatus.PARTIAL_SUCCESS,
            new PersonalInformationUpdateResultResponse(
                new PersonalInformationUpdateResultResponse.Data(
                    new PersonalInformationUpdateResultResponse.Account(
                        "00000000118-0002", "260001373", "2025-12-31", "15:42:52"),
                    List.of(),
                    List.of(
                        new PersonalInformationUpdateResultResponse.AccountErrors(
                            "00000000118-0003", List.of(error))))),
            List.of(
                new UpdatePersonalInformationResponse.Message(
                    "personalInformation.update.partialSuccess")),
            List.of());
    JsonNode json = objectMapper.valueToTree(response);
    assertThat(json)
        .isEqualTo(
            objectMapper.readTree(
                """
        {"success":true,"status":"PARTIAL_SUCCESS",
         "result":{"data":{"selectedAccount":{"accountIdentifier":"00000000118-0002",
         "refNo":"260001373","submitDate":"2025-12-31","submitTime":"15:42:52"},"rows":[],
         "errors":[{"accountIdentifier":"00000000118-0003","errors":[
         {"type":"FIELD","code":"personalInformation.field.required","targets":["emailAddress"]}]}]}},
         "messages":[{"code":"personalInformation.update.partialSuccess"}],"errors":[]}
        """));
  }

  @Test
  void selectedFailureSerializesNullSelectedAndRootErrorsWithoutDuplication() throws Exception {
    var error =
        new MappedError("FIELD", "personalInformation.field.required", List.of("emailAddress"));
    var response =
        new UpdatePersonalInformationResponse(
            false,
            ApiStatus.VALIDATION_FAILED,
            new PersonalInformationUpdateResultResponse(
                new PersonalInformationUpdateResultResponse.Data(
                    null,
                    List.of(
                        new PersonalInformationUpdateResultResponse.Account(
                            "00000000118-0003", null, "", " ")),
                    List.of())),
            List.of(),
            List.of(error));
    JsonNode json = objectMapper.valueToTree(response);
    assertThat(json)
        .isEqualTo(
            objectMapper.readTree(
                """
        {"success":false,"status":"VALIDATION_FAILED",
         "result":{"data":{"selectedAccount":null,"rows":[{"accountIdentifier":"00000000118-0003",
         "refNo":null,"submitDate":"","submitTime":" "}],"errors":[]}},
         "messages":[],"errors":[{"type":"FIELD","code":"personalInformation.field.required","targets":["emailAddress"]}]}
        """));
  }

  @Test
  void nullMetadataAndNullErrorTypeSurviveGlobalNonNullInclusion() {
    var json =
        new ObjectMapper()
            .setDefaultPropertyInclusion(
                JsonInclude.Value.construct(
                    JsonInclude.Include.NON_NULL, JsonInclude.Include.NON_NULL))
            .valueToTree(
                new PersonalInformationUpdateResultResponse(
                    new PersonalInformationUpdateResultResponse.Data(
                        null,
                        List.of(
                            new PersonalInformationUpdateResultResponse.Account(
                                "identifier", null, "", null)),
                        List.of(
                            new PersonalInformationUpdateResultResponse.AccountErrors(
                                "other",
                                List.of(
                                    new MappedError(
                                        null,
                                        "personalInformation.update.rejected",
                                        List.of())))))));
    assertThat(json.path("data").get("selectedAccount").isNull()).isTrue();
    assertThat(json.path("data").path("rows").get(0).get("refNo").isNull()).isTrue();
    assertThat(json.path("data").path("rows").get(0).get("submitTime").isNull()).isTrue();
    assertThat(json.path("data").path("errors").get(0).path("errors").get(0).get("type").isNull())
        .isTrue();
  }

  @Test
  void formSchemaSerializesOptionSetsForSmsLanguages() throws Exception {
    FormSchemaResponse form =
        new FormSchemaResponse(
            "personalInformationForm",
            "1.0",
            "view",
            List.of(),
            Map.of(
                "smsLanguages",
                List.of(
                    new FormOptionResponse("en", "English"),
                    new FormOptionResponse("zh_HK", "Traditional Chinese"))),
            List.of(),
            Map.of(),
            Map.of());
    JsonNode json = objectMapper.readTree(objectMapper.writeValueAsString(form));
    assertThat(json.path("optionSets").has("smsLanguages")).isTrue();
    assertThat(json.path("optionSets").path("smsLanguages").isArray()).isTrue();
    assertThat(json.path("optionSets").path("smsLanguages")).hasSize(2);
    assertThat(json.path("optionSets").path("smsLanguages").get(0).path("value").asText())
        .isEqualTo("en");
    assertThat(json.path("optionSets").path("smsLanguages").get(0).path("label").asText())
        .isEqualTo("English");
    assertThat(json.path("optionSets").path("smsLanguages").get(1).path("value").asText())
        .isEqualTo("zh_HK");
    assertThat(json.path("optionSets").path("smsLanguages").get(1).path("label").asText())
        .isEqualTo("Traditional Chinese");
  }
}
