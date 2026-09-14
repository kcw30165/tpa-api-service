package com.bct.ngtpa.apiservice.adapter.out.redis.idempotency;

import java.util.Base64;

/** Validates and decodes the dedicated runtime HMAC secret without disclosing its value. */
public final class IdempotencyPersistenceSecretFactory {
  private static final int MINIMUM_SECRET_BYTES = 32;

  private IdempotencyPersistenceSecretFactory() {}

  public static byte[] decode(String configuredSecret) {
    if (configuredSecret == null
        || configuredSecret.isBlank()
        || configuredSecret.toLowerCase().contains("change-me")) {
      throw new IllegalStateException(
          "shared idempotency persistence HMAC secret is missing or invalid");
    }
    byte[] decoded;
    try {
      decoded = Base64.getDecoder().decode(configuredSecret.trim());
    } catch (IllegalArgumentException exception) {
      throw new IllegalStateException("shared idempotency persistence HMAC secret is malformed");
    }
    if (decoded.length < MINIMUM_SECRET_BYTES) {
      throw new IllegalStateException("shared idempotency persistence HMAC secret is too short");
    }
    return decoded;
  }
}
