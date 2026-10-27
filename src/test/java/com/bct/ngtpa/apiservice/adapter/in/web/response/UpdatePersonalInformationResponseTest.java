package com.bct.ngtpa.apiservice.adapter.in.web.response;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class UpdatePersonalInformationResponseTest {
  @Test
  void nullMessagesAndErrorsCoerceToEmptyLists() {
    var response = new UpdatePersonalInformationResponse<ApiError>(
        true, ApiStatus.UPDATED, null, null, null);
    assertThat(response.messages()).isEmpty();
    assertThat(response.errors()).isEmpty();
  }

  @Test
  void messagesAndErrorsAreDefensivelyCopied() {
    var messages = new ArrayList<>(List.of(new UpdatePersonalInformationResponse.Message("code")));
    var errors = new ArrayList<>(List.of(ApiError.field("code", null, List.of(), "SERVER")));
    var response = new UpdatePersonalInformationResponse<>(false, ApiStatus.VALIDATION_FAILED,
        null, messages, errors);
    messages.clear();
    errors.clear();
    assertThat(response.messages()).hasSize(1);
    assertThat(response.errors()).hasSize(1);
    assertThatThrownBy(() -> response.messages().clear()).isInstanceOf(UnsupportedOperationException.class);
    assertThatThrownBy(() -> response.errors().clear()).isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void javaPreValidationFactoryKeepsExistingApiErrorUnchanged() {
    var error = ApiError.field("code1", "message1", List.of("field1"), "SERVER");
    var response = UpdatePersonalInformationResponse.validationFailed(List.of(error));
    assertThat(response.success()).isFalse();
    assertThat(response.status()).isEqualTo(ApiStatus.VALIDATION_FAILED);
    assertThat(response.result()).isNull();
    assertThat(response.messages()).isEmpty();
    assertThat(response.errors()).containsExactly(error);
  }
}
