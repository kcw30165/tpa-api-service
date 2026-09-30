package com.bct.ngtpa.apiservice.application.dto.session.authenticated;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Opaque application-owned concurrency token for the generic AuthenticatedSession CAS port.
 *
 * <p>Deliberately a separate type from {@code authflow.SessionRevision}, per the generic-port
 * decision in fund-price-session-contract.md: the two layers (AuthFlowSession vs
 * AuthenticatedSession) must not share a class that would couple one package to the other. Format
 * mirrors the proven {@code authflow.SessionRevision}: 32-character lowercase hex.
 */
public record SessionRevision(String value) {

  private static final Pattern FORMAT = Pattern.compile("[0-9a-f]{32}");

  public SessionRevision {
    Objects.requireNonNull(value, "session revision must not be null");
    value = value.trim();
    if (value.isBlank()) {
      throw new IllegalArgumentException("session revision must not be blank");
    }
    if (!FORMAT.matcher(value).matches()) {
      throw new IllegalArgumentException("session revision format is invalid");
    }
  }

  @Override
  public String toString() {
    return "SessionRevision[value=<redacted>]";
  }
}
