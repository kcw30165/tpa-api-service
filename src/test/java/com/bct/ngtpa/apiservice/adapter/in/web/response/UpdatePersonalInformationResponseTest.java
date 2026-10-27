package com.bct.ngtpa.apiservice.adapter.in.web.response;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class UpdatePersonalInformationResponseTest {
  @Test
  void nullMessagesAndErrorsCoerceToEmptyLists() {
    var response = new UpdatePersonalInformationResponse(true, ApiStatus.UPDATED, null, null, null);
    assertThat(response.messages()).isEmpty();
    assertThat(response.errors()).isEmpty();
  }

  @Test
  void messagesAndErrorsAreDefensivelyCopied() {
    var messages = new ArrayList<>(List.of(new UpdatePersonalInformationResponse.Message("code")));
    var errors = new ArrayList<>(List.of(new MappedError("FIELD", "code", List.of())));
    var response =
        new UpdatePersonalInformationResponse(
            false, ApiStatus.VALIDATION_FAILED, null, messages, errors);
    messages.clear();
    errors.clear();
    assertThat(response.messages()).hasSize(1);
    assertThat(response.errors()).hasSize(1);
    var copiedMessages = response.messages();
    var copiedErrors = response.errors();
    assertThatThrownBy(copiedMessages::clear).isInstanceOf(UnsupportedOperationException.class);
    assertThatThrownBy(copiedErrors::clear).isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void javaPreValidationFactoryKeepsCodeOnlyErrorUnchanged() {
    var error = new MappedError("FIELD", "code1", List.of("field1"));
    var response = UpdatePersonalInformationResponse.validationFailed(List.of(error));
    assertThat(response.success()).isFalse();
    assertThat(response.status()).isEqualTo(ApiStatus.VALIDATION_FAILED);
    assertThat(response.result()).isNull();
    assertThat(response.messages()).isEmpty();
    assertThat(response.errors()).containsExactly(error);
  }
}
