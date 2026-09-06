package com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.idempotency.shared;

import com.bct.ngtpa.apiservice.application.idempotency.IdempotencyFingerprint;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;

/** Produces versioned, length-delimited Forgot Password request fingerprints. */
public final class ForgotPasswordSharedFingerprintFactory {
  public static final int VERSION = 1;

  public IdempotencyFingerprint start(String normalizedUsername) {
    return create(ForgotPasswordSharedIdempotencyAction.START, List.of(required(normalizedUsername)));
  }

  public IdempotencyFingerprint detailsAndOtp(List<String> normalizedDetails) {
    return create(
        ForgotPasswordSharedIdempotencyAction.DETAILS_AND_OTP,
        normalizedDetails == null ? List.of() : normalizedDetails);
  }

  public IdempotencyFingerprint otpResend() {
    return constant(ForgotPasswordSharedIdempotencyAction.OTP_RESEND);
  }

  public IdempotencyFingerprint otpVerify() {
    return constant(ForgotPasswordSharedIdempotencyAction.OTP_VERIFY);
  }

  public IdempotencyFingerprint complete() {
    return constant(ForgotPasswordSharedIdempotencyAction.COMPLETE);
  }

  private IdempotencyFingerprint constant(ForgotPasswordSharedIdempotencyAction action) {
    return create(action, List.of("FORGOT_PASSWORD\u0000" + action.name() + "\u0000v1"));
  }

  private IdempotencyFingerprint create(
      ForgotPasswordSharedIdempotencyAction action, List<String> fields) {
    Objects.requireNonNull(action, "Forgot Password idempotency action must not be null");
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      update(digest, "forgot-password");
      update(digest, action.action().value());
      for (String field : fields) {
        update(digest, field == null ? "" : field);
      }
      return new IdempotencyFingerprint(VERSION, HexFormat.of().formatHex(digest.digest()));
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("Forgot Password fingerprint derivation failed");
    }
  }

  private static void update(MessageDigest digest, String value) {
    byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
    digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(bytes.length).array());
    digest.update(bytes);
  }

  private static String required(String value) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("Forgot Password fingerprint input must not be blank");
    }
    return value.trim();
  }
}
