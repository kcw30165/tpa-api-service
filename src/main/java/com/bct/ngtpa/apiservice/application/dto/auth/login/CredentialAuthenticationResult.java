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
   * Compatibility constructor for existing call sites predating the addition of {@code
   * trustCode} / {@code schemeName} / {@code schemeNameChi} (modeled ahead of the downstream
   * TAS/APIM change; see
   * docs/design/10-template-service/template-service-implementation-plan.md section D.11.1). All
   * three new fields default to {@code null}.
   */
  public CredentialAuthenticationResult(
      CredentialAuthenticationStatus status,
      String normalizedUsername,
      String userRole,
      Integer failCount,
      List<LoginCredentialDestination> destinations,
      String downstreamCode) {
    this(
        status,
        normalizedUsername,
        userRole,
        failCount,
        destinations,
        downstreamCode,
        null,
        null,
        null);
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
