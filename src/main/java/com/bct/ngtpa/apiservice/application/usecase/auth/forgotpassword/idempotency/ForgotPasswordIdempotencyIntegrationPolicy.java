package com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.idempotency;

import com.bct.ngtpa.apiservice.application.idempotency.CanonicalIdempotencyFingerprintFactory;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotencyCapability;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotencyFingerprint;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotencyOperationId;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotencyOperationIdentityFactory;
import java.util.List;
import java.util.Objects;

/** Forgot Password policy inputs over the single repository-wide shared foundation. */
public final class ForgotPasswordIdempotencyIntegrationPolicy {
  private static final int FINGERPRINT_VERSION = 1;
  private static final IdempotencyCapability CAPABILITY =
      new IdempotencyCapability("forgot-password");

  private final IdempotencyOperationIdentityFactory identities;
  private final CanonicalIdempotencyFingerprintFactory fingerprints;

  public ForgotPasswordIdempotencyIntegrationPolicy(
      IdempotencyOperationIdentityFactory identities,
      CanonicalIdempotencyFingerprintFactory fingerprints) {
    this.identities = Objects.requireNonNull(identities);
    this.fingerprints = Objects.requireNonNull(fingerprints);
  }

  public IdempotencyOperationId operationId(
      ForgotPasswordIdempotencyAction action,
      String rawAuthorizedScope,
      String clientIdempotencyKey) {
    Objects.requireNonNull(action, "Forgot Password action must not be null");
    return identities.create(CAPABILITY, action.action(), rawAuthorizedScope, clientIdempotencyKey);
  }

  public IdempotencyFingerprint start(String normalizedUsername) {
    return fingerprint(
        ForgotPasswordIdempotencyAction.START, List.of(required(normalizedUsername)));
  }

  public IdempotencyFingerprint detailsAndOtp(List<String> normalizedDetails) {
    return fingerprint(
        ForgotPasswordIdempotencyAction.DETAILS_AND_OTP,
        normalizedDetails == null ? List.of() : normalizedDetails);
  }

  public IdempotencyFingerprint otpResend() {
    return fingerprint(ForgotPasswordIdempotencyAction.OTP_RESEND, List.of("v1"));
  }

  public IdempotencyFingerprint otpVerify() {
    return fingerprint(ForgotPasswordIdempotencyAction.OTP_VERIFY, List.of("v1"));
  }

  public IdempotencyFingerprint complete() {
    return fingerprint(ForgotPasswordIdempotencyAction.COMPLETE, List.of("v1"));
  }

  private IdempotencyFingerprint fingerprint(
      ForgotPasswordIdempotencyAction action, List<String> fields) {
    return fingerprints.create(FINGERPRINT_VERSION, CAPABILITY, action.action(), fields);
  }

  private static String required(String value) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("Forgot Password fingerprint input must not be blank");
    }
    return value.trim();
  }
}
