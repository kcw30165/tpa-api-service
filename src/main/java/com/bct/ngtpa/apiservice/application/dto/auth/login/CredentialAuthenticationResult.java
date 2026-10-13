package com.bct.ngtpa.apiservice.application.dto.auth.login;

import java.util.List;
import java.util.Objects;

public record CredentialAuthenticationResult(
    CredentialAuthenticationStatus status,
    String normalizedUsername,
    String userRole,
    Integer failCount,
    List<LoginCredentialDestination> destinations,
    String downstreamCode,
    String trustCode,
    String schemeName,
    String schemeNameChi) {
  public CredentialAuthenticationResult {
    Objects.requireNonNull(status, "status must not be null");
    destinations = destinations == null ? List.of() : List.copyOf(destinations);
    downstreamCode = null;
  }

  /**
   * Creates the result of a failed credential check: no authenticated user, role, destinations or
   * trust and scheme fields, only the failure status and the downstream fail count.
   */
  public static CredentialAuthenticationResult failure(
      CredentialAuthenticationStatus status, Integer failCount) {
    return new CredentialAuthenticationResult(
        status, null, null, failCount, List.of(), null, null, null, null);
  }

  @Override
  public String toString() {
    return "CredentialAuthenticationResult[status="
        + status
        + ", normalizedUsername=<redacted>, userRole="
        + userRole
        + ", failCount="
        + failCount
        + ", destinationsCount="
        + destinations.size()
        + ", downstreamCode=<redacted>, trustCode=<redacted>, schemeName=<redacted>,"
        + " schemeNameChi=<redacted>]";
  }
}
