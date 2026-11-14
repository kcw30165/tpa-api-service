package com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.otp.verify;

/** Fixed, value-free technical incident metadata for OTP_VERIFY. */
public enum ForgotPasswordOtpVerifyIncident {
  LOCK_ACQUISITION("OTP_VERIFY_TECHNICAL_FAILURE", "lock-acquisition", true),
  SESSION_READ("OTP_VERIFY_TECHNICAL_FAILURE", "session-read", true),
  SESSION_REREAD("OTP_VERIFY_TECHNICAL_FAILURE", "session-reread", true),
  CONTEXT_LOAD("OTP_VERIFY_TECHNICAL_FAILURE", "context-load", true),
  CHALLENGE_VERIFICATION("OTP_VERIFY_TECHNICAL_FAILURE", "challenge-verification", true),
  AUTHORIZATION_PROTECTION("OTP_VERIFY_TECHNICAL_FAILURE", "authorization-protection", true),
  SESSION_UPDATE("OTP_VERIFY_TECHNICAL_FAILURE", "session-update", true),
  EXPIRY_REFRESH("OTP_VERIFY_TECHNICAL_FAILURE", "expiry-refresh", true),
  PROTECTED_OPERATION("OTP_VERIFY_TECHNICAL_FAILURE", "protected-operation", true),
  IDEMPOTENCY("OTP_VERIFY_IDEMPOTENCY_FAILURE", "idempotency", true),
  LOCK_RELEASE("OTP_VERIFY_TECHNICAL_FAILURE", "lock-release", false);

  private final String internalCode;
  private final String stage;
  private final boolean terminalPublicationRequired;

  ForgotPasswordOtpVerifyIncident(
      String internalCode, String stage, boolean terminalPublicationRequired) {
    this.internalCode = internalCode;
    this.stage = stage;
    this.terminalPublicationRequired = terminalPublicationRequired;
  }

  public String internalCode() {
    return internalCode;
  }

  public String stage() {
    return stage;
  }

  public boolean terminalPublicationRequired() {
    return terminalPublicationRequired;
  }
}
