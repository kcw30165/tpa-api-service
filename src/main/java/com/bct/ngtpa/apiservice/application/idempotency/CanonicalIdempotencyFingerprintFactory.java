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
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      update(digest, capability.value());
      update(digest, action.value());
      for (String field : canonicalFields == null ? List.<String>of() : canonicalFields) {
        update(digest, field == null ? "" : field);
      }
      return new IdempotencyFingerprint(version, HexFormat.of().formatHex(digest.digest()));
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 is unavailable", exception);
    }
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
