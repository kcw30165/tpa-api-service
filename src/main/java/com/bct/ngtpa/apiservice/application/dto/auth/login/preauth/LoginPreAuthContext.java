package com.bct.ngtpa.apiservice.application.dto.auth.login.preauth;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public record LoginPreAuthContext(
    String normalizedUsername,
    String userRole,
    Map<String, LoginPreAuthDestination> destinationsById,
    Instant credentialVerifiedAt,
    String workflowVersion,
    String trustCode,
    String schemeName,
    String schemeNameChi) {
  public LoginPreAuthContext {
    Objects.requireNonNull(normalizedUsername);

    destinationsById =
        destinationsById == null
            ? Map.of()
            : java.util.Collections.unmodifiableMap(new LinkedHashMap<>(destinationsById));
    Objects.requireNonNull(credentialVerifiedAt);
    Objects.requireNonNull(workflowVersion);
  }

  /**
   * Compatibility constructor for existing call sites predating the addition of {@code
   * trustCode} / {@code schemeName} / {@code schemeNameChi} (modeled ahead of the downstream
   * TAS/APIM change; see
   * docs/design/10-template-service/template-service-implementation-plan.md section D.11.1). All
   * three new fields default to {@code null}.
   */
  public LoginPreAuthContext(
      String normalizedUsername,
      String userRole,
      Map<String, LoginPreAuthDestination> destinationsById,
      Instant credentialVerifiedAt,
      String workflowVersion) {
    this(
        normalizedUsername,
        userRole,
        destinationsById,
        credentialVerifiedAt,
        workflowVersion,
        null,
        null,
        null);
  }

  @Override
  public String toString() {
    return "LoginPreAuthContext[normalizedUsername=<redacted>, userRole="
        + userRole
        + ", destinationsCount="
        + destinationsById.size()
        + ", credentialVerifiedAt="
        + credentialVerifiedAt
        + ", workflowVersion="
        + workflowVersion
        + ", trustCode=<redacted>, schemeName=<redacted>, schemeNameChi=<redacted>]";
  }
}
