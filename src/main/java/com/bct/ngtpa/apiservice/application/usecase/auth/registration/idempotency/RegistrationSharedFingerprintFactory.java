package com.bct.ngtpa.apiservice.application.usecase.auth.registration.idempotency;

import com.bct.ngtpa.apiservice.application.idempotency.IdempotencyFingerprint;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;

/** Produces versioned request-equivalence fingerprints from normalized action-owned fields. */
public final class RegistrationSharedFingerprintFactory {
  public static final int VERSION = 1;

  public IdempotencyFingerprint create(
      RegistrationSharedIdempotencyAction action, List<String> normalizedParts) {
    Objects.requireNonNull(action, "registration idempotency action must not be null");
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      update(digest, action.value());
      for (String part : normalizedParts == null ? List.<String>of() : normalizedParts) {
        update(digest, part == null ? "" : part);
      }
      return new IdempotencyFingerprint(VERSION, HexFormat.of().formatHex(digest.digest()));
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("Registration idempotency fingerprint derivation failed");
    }
  }

  private static void update(MessageDigest digest, String value) {
    byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
    digest.update((byte) ((bytes.length >>> 24) & 0xff));
    digest.update((byte) ((bytes.length >>> 16) & 0xff));
    digest.update((byte) ((bytes.length >>> 8) & 0xff));
    digest.update((byte) (bytes.length & 0xff));
    digest.update(bytes);
  }
}
