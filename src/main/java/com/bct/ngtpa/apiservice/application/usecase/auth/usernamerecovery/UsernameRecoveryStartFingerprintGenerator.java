package com.bct.ngtpa.apiservice.application.usecase.auth.usernamerecovery;

import com.bct.ngtpa.apiservice.application.dto.auth.usernamerecovery.UsernameRecoveryStartCommand;
import com.bct.ngtpa.apiservice.application.port.out.UsernameRecoveryIdempotencyAction;
import com.bct.ngtpa.apiservice.application.port.out.UsernameRecoveryIdempotencyFingerprint;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;
import java.util.Objects;

/**
 * Deterministic, unlogged semantic fingerprint for the Username Recovery START idempotency
 * boundary.
 *
 * <p>Design references: {@code
 * docs/design/6-username-recovery/username-recovery-session-redis-design.md} section 1 (recovery
 * idempotency hash; the captcha answer is never included) and section 8 (safe persistence). The
 * digest covers the START action namespace plus the normalized identity type/value, DOB,
 * exactly-one contact, and captcha challenge id. Raw identity, DOB, contact, and captcha values are
 * consumed only transiently and never retained or logged.
 */
public final class UsernameRecoveryStartFingerprintGenerator {

  public UsernameRecoveryIdempotencyFingerprint derive(UsernameRecoveryStartCommand command) {
    Objects.requireNonNull(command, "command must not be null");
    MessageDigest digest = newDigest();
    updatePart(digest, UsernameRecoveryIdempotencyAction.START.keyNamespace());
    updatePart(digest, command.identityType().name());
    updatePart(digest, command.identityValue().trim());
    updatePart(digest, command.dateOfBirth().trim());
    updatePart(digest, normalizeEmail(command.email()));
    updatePart(digest, normalizeMobile(command.mobile()));
    updatePart(digest, command.captchaChallengeId().trim());
    updatePart(digest, command.language().toLanguageTag());
    return new UsernameRecoveryIdempotencyFingerprint(toHex(digest.digest()));
  }

  private static String normalizeEmail(String email) {
    return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
  }

  private static String normalizeMobile(String mobile) {
    return mobile == null ? "" : mobile.trim();
  }

  private static void updatePart(MessageDigest digest, String part) {
    byte[] bytes = part.getBytes(StandardCharsets.UTF_8);
    digest.update(Integer.toString(bytes.length).getBytes(StandardCharsets.UTF_8));
    digest.update((byte) 0);
    digest.update(bytes);
    digest.update((byte) 0);
  }

  private static MessageDigest newDigest() {
    try {
      return MessageDigest.getInstance("SHA-256");
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 digest is unavailable", exception);
    }
  }

  private static String toHex(byte[] bytes) {
    StringBuilder builder = new StringBuilder(bytes.length * 2);
    for (byte value : bytes) {
      builder.append(String.format("%02x", value));
    }
    return builder.toString();
  }
}
