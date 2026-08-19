package com.bct.ngtpa.apiservice.adapter.in.web.auth.login.dto;

import java.util.List;

public record LoginStartDataResponse(
    String csrfToken,
    List<LoginDestinationResponse> destinations,
    Integer expiresInSeconds,
    List<String> actions,
    Integer failCount) {
  public LoginStartDataResponse {
    destinations = destinations == null ? List.of() : List.copyOf(destinations);
    actions = actions == null ? List.of() : List.copyOf(actions);
  }

  @Override
  public String toString() {
    return "LoginStartDataResponse[csrfToken=<redacted>, destinationsCount="
        + destinations.size()
        + ", expiresInSeconds="
        + expiresInSeconds
        + ", actions="
        + actions
        + ", failCount="
        + failCount
        + "]";
  }
}
