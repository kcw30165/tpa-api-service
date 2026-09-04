package com.bct.ngtpa.apiservice.application.usecase.auth.login.idempotency.shared;

import com.bct.ngtpa.apiservice.application.idempotency.IdempotencyFingerprint;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;

/** Creates version-1, length-delimited, action-separated Login request fingerprints. */
public final class LoginSharedFingerprintFactory {
  public static final int VERSION = 1;

  public IdempotencyFingerprint credentials(String normalizedUsername, String password) {
    return create(
        LoginSharedIdempotencyAction.CREDENTIALS,
        List.of(required(normalizedUsername), digest(password)));
  }

  public IdempotencyFingerprint otpSend(String authFlowSessionId, String destinationId) {
    return create(
        LoginSharedIdempotencyAction.OTP_SEND,
        List.of(required(authFlowSessionId), required(destinationId)));
  }

  public IdempotencyFingerprint otpResend(String authFlowSessionId) {
    return create(LoginSharedIdempotencyAction.OTP_RESEND, List.of(required(authFlowSessionId)));
  }

  public IdempotencyFingerprint otpVerify() {
    return create(
        LoginSharedIdempotencyAction.OTP_VERIFY, List.of("LOGIN\u0000OTP_VERIFY\u0000v1"));
  }

  public String oneWayDigest(String protectedValue) {
    return digest(protectedValue);
  }

  private IdempotencyFingerprint create(LoginSharedIdempotencyAction action, List<String> fields) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      update(digest, "login");
      update(digest, action.action().value());
      for (String field : fields) {
        update(digest, field);
      }
      return new IdempotencyFingerprint(VERSION, HexFormat.of().formatHex(digest.digest()));
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 is unavailable", exception);
    }
  }

  private void update(MessageDigest digest, String value) {
    byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
    digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(bytes.length).array());
    digest.update(bytes);
  }

  private String digest(String value) {
    String required = required(value);
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256")
                  .digest(required.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 is unavailable", exception);
    }
  }

  private String required(String value) {
    Objects.requireNonNull(value, "Login fingerprint input must not be null");
    if (value.isBlank()) {
      throw new IllegalArgumentException("Login fingerprint input must not be blank");
    }
    return value;
  }
}
