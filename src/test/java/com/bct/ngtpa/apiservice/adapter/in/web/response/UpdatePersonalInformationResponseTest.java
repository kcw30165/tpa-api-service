package com.bct.ngtpa.apiservice.adapter.in.web.response;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

/** Branch-oriented tests for {@link UpdatePersonalInformationResponse}. */
class UpdatePersonalInformationResponseTest {

  // ----- compact constructor null-coercion -------------------------------------

  @Test
  void nullMessagesAndErrorsCoerceToEmptyLists() {
    UpdatePersonalInformationResponse response =
        new UpdatePersonalInformationResponse(true, "SUCCESS", null, null, null);
    assertThat(response.messages()).isEmpty();
    assertThat(response.errors()).isEmpty();
  }

  // ----- updated() factory --------------------------------------------------------

  @Test
  void updatedFactorySetsSuccessTrueAndIncludesSuccessMessage() {
    PersonalInformationUpdateResultResponse result =
        new PersonalInformationUpdateResultResponse("ref-1", "2026-09-14", "10:00:00");
    UpdatePersonalInformationResponse response =
        UpdatePersonalInformationResponse.updated(List.of(result));
    assertThat(response.success()).isTrue();
    assertThat(response.status()).isEqualTo(ApiStatus.UPDATED.toString());
    assertThat(response.result()).containsExactly(result);
    assertThat(response.messages()).hasSize(1);
    assertThat(response.messages().get(0).type()).isEqualTo("SUCCESS");
    assertThat(response.errors()).isEmpty();
  }

  // ----- validationFailed() factory ------------------------------------------------

  @Test
  void validationFailedFactorySetsSuccessFalseWithErrors() {
    ApiError error = ApiError.field("code1", "message1", List.of("field1"), "SERVER");
    UpdatePersonalInformationResponse response =
        UpdatePersonalInformationResponse.validationFailed(List.of(error));
    assertThat(response.success()).isFalse();
    assertThat(response.status()).isEqualTo(ApiStatus.VALIDATION_FAILED.toString());
    assertThat(response.result()).isNull();
    assertThat(response.errors()).containsExactly(error);
  }

  // ----- businessRejected() factory ------------------------------------------------

  @Test
  void businessRejectedFactorySetsSuccessFalseWithErrors() {
    ApiError error = ApiError.business("code2", "message2", List.of(), "SERVER");
    UpdatePersonalInformationResponse response =
        UpdatePersonalInformationResponse.businessRejected(List.of(error));
    assertThat(response.success()).isFalse();
    assertThat(response.status()).isEqualTo(ApiStatus.BUSINESS_REJECTED.toString());
    assertThat(response.errors()).containsExactly(error);
  }

  // ----- systemError() factory --------------------------------------------------

  @Test
  void systemErrorFactorySetsSuccessFalseWithErrors() {
    ApiError error = ApiError.system("code3", "message3", "SERVER");
    UpdatePersonalInformationResponse response =
        UpdatePersonalInformationResponse.systemError(List.of(error));
    assertThat(response.success()).isFalse();
    assertThat(response.status()).isEqualTo(ApiStatus.SYSTEM_ERROR.toString());
    assertThat(response.errors()).containsExactly(error);
  }
}
