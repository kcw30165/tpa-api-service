package com.bct.ngtpa.apiservice.adapter.in.web.response;

import java.util.List;

public record RegistrationCancellationPageResponse(
    String pageId, List<RegistrationCancellationPageActionResponse> actions) {
  public RegistrationCancellationPageResponse {
    actions = actions == null ? List.of() : List.copyOf(actions);
  }
}
