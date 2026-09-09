package com.bct.ngtpa.apiservice.application.usecase.auth.usernamerecovery.idempotency.start;

/** Strict encrypted wire envelope for Username Recovery START replay continuity. */
public record ProtectedUsernameRecoveryStartReplay(
    int schemaVersion, String keyVersion, String algorithm, String iv, String ciphertext) {
  public static final int CURRENT_SCHEMA_VERSION = 1;

  public ProtectedUsernameRecoveryStartReplay {
    if (schemaVersion != CURRENT_SCHEMA_VERSION) {
      throw new IllegalArgumentException("Username Recovery START replay schema is unsupported");
    }
    keyVersion = require(keyVersion, "key version");
    algorithm = require(algorithm, "algorithm");
    iv = require(iv, "iv");
    ciphertext = require(ciphertext, "ciphertext");
  }

  @Override
  public String toString() {
    return "ProtectedUsernameRecoveryStartReplay[schemaVersion="
        + schemaVersion
        + ", keyVersion="
        + keyVersion
        + ", algorithm="
        + algorithm
        + ", iv=<redacted>, ciphertext=<redacted>]";
  }

  private static String require(String value, String label) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("Username Recovery START replay " + label + " is missing");
    }
    return value.trim();
  }
}
