package com.bct.ngtpa.apiservice.adapter.in.web.mapper;

import static com.bct.ngtpa.apiservice.adapter.in.web.mapper.PersonalInformationUpdateResponseMapperTest.mapper;
import static com.bct.ngtpa.apiservice.adapter.in.web.mapper.PersonalInformationUpdateResponseMapperTest.row;
import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.adapter.in.web.response.ApiStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;

class PersonalInformationUpdateResultListMapperContractTest {
  @Test
  void listInputBecomesDataObjectNotListOutput() {
    var json =
        new ObjectMapper()
            .valueToTree(mapper().toResponse(List.of(row(true, true, "2"), row(false, true, "3"))));
    assertThat(json.path("result").isObject()).isTrue();
    assertThat(json.path("result").path("data").path("rows")).hasSize(1);
    assertThat(
            json.path("result")
                .path("data")
                .path("selectedAccount")
                .path("accountIdentifier")
                .asText())
        .isEqualTo("00000000118-0002");
  }

  @Test
  void selectedFailureWithOtherSuccessRetainsOtherRowsButHasValidationFailedStatus() {
    var response = mapper().toResponse(List.of(row(false, true, "3"), row(true, false, "2")));
    assertThat(response.status()).isEqualTo(ApiStatus.VALIDATION_FAILED);
    assertThat(response.messages()).isEmpty();
    assertThat(response.errors()).hasSize(1);
    assertThat(response.result().data().selectedAccount()).isNull();
    assertThat(response.result().data().rows()).hasSize(1);
    assertThat(response.result().data().errors()).isEmpty();
  }

  @Test
  void selectedSuccessWithOtherFailureRetainsOnlyOtherErrorsInData() {
    var response = mapper().toResponse(List.of(row(true, true, "2"), row(false, false, "3")));
    assertThat(response.status()).isEqualTo(ApiStatus.PARTIAL_SUCCESS);
    assertThat(response.errors()).isEmpty();
    assertThat(response.result().data().rows()).isEmpty();
    assertThat(response.result().data().errors()).hasSize(1);
    assertThat(response.messages().getFirst().code())
        .isEqualTo("personalInformation.update.partialSuccess");
  }
}
