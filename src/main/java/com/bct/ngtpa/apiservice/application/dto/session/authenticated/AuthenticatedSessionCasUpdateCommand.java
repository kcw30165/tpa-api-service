package com.bct.ngtpa.apiservice.application.dto.session.authenticated;

import java.time.Instant;
import java.util.Objects;

/**
 * Command for the generic AuthenticatedSession compare-and-swap update.
 *
 * <p>The caller is responsible for loading the current session, copying every field verbatim except
 * the field(s) under mutation, and supplying both the expected and replacement revisions here. The
 * port itself never inspects which field changed.
 *
 * @param expiryDeadline null to preserve the current PTTL atomically; otherwise the absolute
 *     epoch-millisecond deadline to validate against Redis TIME and apply inside the CAS script
 */
public record AuthenticatedSessionCasUpdateCommand(
    AuthenticatedSessionId authenticatedSessionId,
    SessionRevision expectedRevision,
    ProtectedAuthenticatedSession replacementProtectedSession,
    SessionRevision replacementRevision,
    String correlationId,
    Instant expiryDeadline) {

  /** Existing consumers preserve the current Redis TTL entirely within the CAS script. */
  public AuthenticatedSessionCasUpdateCommand(
      AuthenticatedSessionId authenticatedSessionId,
      SessionRevision expectedRevision,
      ProtectedAuthenticatedSession replacementProtectedSession,
      SessionRevision replacementRevision,
      String correlationId) {
    this(
        authenticatedSessionId,
        expectedRevision,
        replacementProtectedSession,
        replacementRevision,
        correlationId,
        null);
  }

  public AuthenticatedSessionCasUpdateCommand {
    Objects.requireNonNull(authenticatedSessionId, "authenticatedSessionId must not be null");
    Objects.requireNonNull(expectedRevision, "expectedRevision must not be null");
    Objects.requireNonNull(
        replacementProtectedSession, "replacementProtectedSession must not be null");
    Objects.requireNonNull(replacementRevision, "replacementRevision must not be null");
    if (expectedRevision.equals(replacementRevision)) {
      throw new IllegalArgumentException("replacement revision must differ from expected");
    }
    correlationId = correlationId == null || correlationId.isBlank() ? null : correlationId.trim();
  }

  @Override
  public String toString() {
    return "AuthenticatedSessionCasUpdateCommand[<redacted>]";
  }
}
