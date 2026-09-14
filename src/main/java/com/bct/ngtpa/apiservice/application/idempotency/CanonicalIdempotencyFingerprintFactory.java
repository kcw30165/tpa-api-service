package com.bct.ngtpa.apiservice.application.idempotency;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;

/** Central versioned, length-delimited SHA-256 fingerprint construction. */
public final class CanonicalIdempotencyFingerprintFactory {
  public IdempotencyFingerprint create(
      int version,
      IdempotencyCapability capability,
      IdempotencyAction action,
      List<String> canonicalFields) {
    if (version <= 0) throw new IllegalArgumentException("fingerprint version must be positive");
    Objects.requireNonNull(capability, "capability must not be null");
    Objects.requireNonNull(action, "action must not be null");
    return createFromCanonicalParts(
        version, combine(capability.value(), action.value(), canonicalFields));
  }

  /**
   * Compatibility-preserving central hashing for integrations with an established canonical wire.
   */
  public IdempotencyFingerprint createFromCanonicalParts(int version, List<String> canonicalParts) {
    if (version <= 0) throw new IllegalArgumentException("fingerprint version must be positive");
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      for (String part : canonicalParts == null ? List.<String>of() : canonicalParts) {
        update(digest, part == null ? "" : part);
      }
      return new IdempotencyFingerprint(version, HexFormat.of().formatHex(digest.digest()));
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 is unavailable", exception);
    }
  }

  private static List<String> combine(
      String capability, String action, List<String> canonicalFields) {
    var parts = new java.util.ArrayList<String>();
    parts.add(capability);
    parts.add(action);
    if (canonicalFields != null) parts.addAll(canonicalFields);
    return List.copyOf(parts);
  }

  public String oneWayDigest(String protectedValue) {
    if (protectedValue == null || protectedValue.isBlank()) {
      throw new IllegalArgumentException("protected fingerprint input must not be blank");
    }
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256")
                  .digest(protectedValue.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 is unavailable", exception);
    }
  }

  private static void update(MessageDigest digest, String value) {
    byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
    digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(bytes.length).array());
    digest.update(bytes);
  }
}
