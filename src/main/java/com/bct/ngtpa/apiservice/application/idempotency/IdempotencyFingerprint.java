package com.bct.ngtpa.apiservice.application.idempotency;

import java.util.Locale;
import java.util.Objects;

/** Versioned canonical request fingerprint represented as a lowercase SHA-256 hex value. */
public record IdempotencyFingerprint(int version, String value) {
  public IdempotencyFingerprint {
    if (version <= 0) {
      throw new IllegalArgumentException("idempotency fingerprint version must be positive");
    }
    Objects.requireNonNull(value, "idempotency fingerprint must not be null");
    value = value.toLowerCase(Locale.ROOT);
    if (!value.matches("[0-9a-f]{64}")) {
      throw new IllegalArgumentException("idempotency fingerprint is invalid");
    }
  }

  @Override
  public String toString() {
    return "IdempotencyFingerprint[version=" + version + ", value=<redacted>]";
  }
}
