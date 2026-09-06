package com.bct.ngtpa.apiservice.application.idempotency;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

/** Central construction of opaque operation identities for every integrating flow. */
public final class IdempotencyOperationIdentityFactory {
  public IdempotencyOperationId create(
      IdempotencyCapability capability,
      IdempotencyAction action,
      String rawAuthorizedScope,
      String clientIdempotencyKey) {
    Objects.requireNonNull(capability, "capability must not be null");
    Objects.requireNonNull(action, "action must not be null");
    return new IdempotencyOperationId(
        capability,
        action,
        new IdempotencyOperationScope(sha256(requireText(rawAuthorizedScope, "operation scope"))),
        new IdempotencyKey(requireText(clientIdempotencyKey, "client idempotency key")));
  }

  private static String requireText(String value, String label) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("idempotency " + label + " must not be blank");
    }
    return value.trim();
  }

  private static String sha256(String value) {
    try {
      return HexFormat.of()
          .formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 is unavailable", exception);
    }
  }
}
