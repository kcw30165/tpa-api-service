package com.bct.ngtpa.apiservice.application.usecase.auth.usernamerecovery;

import com.bct.ngtpa.apiservice.application.dto.auth.usernamerecovery.UsernameRecoveryRequestCommand;
import com.bct.ngtpa.apiservice.application.port.out.UsernameRecoveryIdempotencyAction;
import com.bct.ngtpa.apiservice.application.port.out.UsernameRecoveryIdempotencyFingerprint;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Objects;

/**
 * Deterministic, unlogged semantic fingerprint for the Username Recovery REQUEST idempotency
 * boundary.
 *
 * <p>Design references: {@code
 * docs/design/6-username-recovery/username-recovery-session-redis-design.md} section 1 (recovery
 * idempotency hash) and section 8 (safe persistence). The digest covers the REQUEST action
 * namespace plus the protected session id and the opaque session-bound Scheme-Ref - never the
 * client-provided idempotency key, never the username, destination, CSRF, identity, or DOB. Raw
 * values are consumed only transiently and never retained or logged.
 */
public final class UsernameRecoveryRequestFingerprintGenerator {

  public UsernameRecoveryIdempotencyFingerprint derive(UsernameRecoveryRequestCommand command) {
    Objects.requireNonNull(command, "command must not be null");
    MessageDigest digest = newDigest();
    updatePart(digest, UsernameRecoveryIdempotencyAction.REQUEST.keyNamespace());
    updatePart(digest, command.authFlowSessionId().value());
    updatePart(digest, command.schemeRef().value());
    return new UsernameRecoveryIdempotencyFingerprint(toHex(digest.digest()));
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
