package com.bct.ngtpa.apiservice.application.usecase.auth.login.idempotency;

import com.bct.ngtpa.apiservice.application.idempotency.CanonicalIdempotencyFingerprintFactory;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotencyFingerprint;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Compatibility API supplying Login canonical fields to the central fingerprint factory. */
public final class LoginFingerprintFactory {
  public static final int VERSION = 1;
  private final CanonicalIdempotencyFingerprintFactory delegate;

  public LoginFingerprintFactory() {
    this(new CanonicalIdempotencyFingerprintFactory());
  }

  LoginFingerprintFactory(CanonicalIdempotencyFingerprintFactory delegate) {
    this.delegate = Objects.requireNonNull(delegate);
  }

  public IdempotencyFingerprint credentials(String normalizedUsername, String password) {
    return create(
        LoginIdempotencyAction.CREDENTIALS,
        List.of(required(normalizedUsername), oneWayDigest(password)));
  }

  public IdempotencyFingerprint otpSend(String authFlowSessionId, String destinationId) {
    return create(
        LoginIdempotencyAction.OTP_SEND,
        List.of(required(authFlowSessionId), required(destinationId)));
  }

  public IdempotencyFingerprint otpResend(String authFlowSessionId) {
    return create(LoginIdempotencyAction.OTP_RESEND, List.of(required(authFlowSessionId)));
  }

  public IdempotencyFingerprint otpVerify() {
    return create(LoginIdempotencyAction.OTP_VERIFY, List.of("LOGIN\u0000OTP_VERIFY\u0000v1"));
  }

  public String oneWayDigest(String protectedValue) {
    return delegate.oneWayDigest(required(protectedValue));
  }

  private IdempotencyFingerprint create(LoginIdempotencyAction action, List<String> fields) {
    var parts = new ArrayList<String>();
    parts.add("login");
    parts.add(action.action().value());
    parts.addAll(fields);
    return delegate.createFromCanonicalParts(VERSION, parts);
  }

  private static String required(String value) {
    Objects.requireNonNull(value, "Login fingerprint input must not be null");
    if (value.isBlank())
      throw new IllegalArgumentException("Login fingerprint input must not be blank");
    return value;
  }
}
