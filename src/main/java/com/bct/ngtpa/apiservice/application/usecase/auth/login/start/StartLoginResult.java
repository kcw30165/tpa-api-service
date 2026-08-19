package com.bct.ngtpa.apiservice.application.usecase.auth.login.start;

import java.util.List;

public record StartLoginResult(
    StartLoginStatus status,
    String authFlowSessionId,
    String csrfToken,
    Integer expiresInSeconds,
    List<LoginDestinationChoice> destinations,
    Integer failCount,
    String replayCommitRef) {
  public StartLoginResult {
    destinations = destinations == null ? List.of() : List.copyOf(destinations);
  }

  @Override
  public String toString() {
    return "StartLoginResult[status="
        + status
        + ", authFlowSessionId=<redacted>, csrfToken=<redacted>, expiresInSeconds="
        + expiresInSeconds
        + ", destinationsCount="
        + destinations.size()
        + ", failCount="
        + failCount
        + ", replayCommitRef=<redacted>]";
  }
}
