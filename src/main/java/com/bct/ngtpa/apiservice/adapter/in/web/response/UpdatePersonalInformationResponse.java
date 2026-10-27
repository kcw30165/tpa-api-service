package com.bct.ngtpa.apiservice.adapter.in.web.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/** PUT envelope; Java pre-validation retains ApiError until its separate contract migration. */
public record UpdatePersonalInformationResponse<E>(
    boolean success,
    ApiStatus status,
    @JsonInclude(JsonInclude.Include.ALWAYS) PersonalInformationUpdateResultResponse result,
    List<Message> messages,
    List<E> errors) {

  public UpdatePersonalInformationResponse {
    messages = messages == null ? List.of() : List.copyOf(messages);
    errors = errors == null ? List.of() : List.copyOf(errors);
  }

  public record Message(String code) {}

  public static UpdatePersonalInformationResponse<ApiError> validationFailed(List<ApiError> errors) {
    return new UpdatePersonalInformationResponse<>(
        false, ApiStatus.VALIDATION_FAILED, null, List.of(), errors);
  }
}
