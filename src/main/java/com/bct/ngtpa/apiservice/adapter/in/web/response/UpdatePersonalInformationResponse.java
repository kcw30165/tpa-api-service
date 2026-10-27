package com.bct.ngtpa.apiservice.adapter.in.web.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/** Code-only PUT envelope for account outcomes and Java content rejections. */
public record UpdatePersonalInformationResponse(
    boolean success,
    ApiStatus status,
    @JsonInclude(JsonInclude.Include.ALWAYS) PersonalInformationUpdateResultResponse result,
    List<Message> messages,
    List<MappedError> errors) {

  public UpdatePersonalInformationResponse {
    messages = messages == null ? List.of() : List.copyOf(messages);
    errors = errors == null ? List.of() : List.copyOf(errors);
  }

  public record Message(String code) {}

  public static UpdatePersonalInformationResponse validationFailed(List<MappedError> errors) {
    return new UpdatePersonalInformationResponse(
        false, ApiStatus.VALIDATION_FAILED, null, List.of(), errors);
  }
}
