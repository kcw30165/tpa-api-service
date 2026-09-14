package com.bct.ngtpa.apiservice.application.idempotency;

import java.util.Objects;

/** Opaque reference to separately protected replay material. */
public record ProtectedReplayReference(String value) {
  private static final String PATTERN = "[A-Za-z0-9_-]{16,256}";

  public ProtectedReplayReference {
    Objects.requireNonNull(value, "protected replay reference must not be null");
    value = value.trim();
    if (!value.matches(PATTERN)) {
      throw new IllegalArgumentException("protected replay reference is invalid");
    }
  }

  @Override
  public String toString() {
    return "ProtectedReplayReference[value=<redacted>]";
  }
}
