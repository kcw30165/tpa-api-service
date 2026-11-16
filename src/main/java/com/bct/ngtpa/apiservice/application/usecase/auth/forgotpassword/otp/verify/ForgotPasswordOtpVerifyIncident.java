package com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.otp.verify;

/** Fixed, value-free technical incident metadata for OTP_VERIFY. */
public enum ForgotPasswordOtpVerifyIncident {
  LOCK_ACQUISITION(Codes.TECHNICAL_FAILURE, "lock-acquisition", true),
  SESSION_READ(Codes.TECHNICAL_FAILURE, "session-read", true),
  SESSION_REREAD(Codes.TECHNICAL_FAILURE, "session-reread", true),
  CONTEXT_LOAD(Codes.TECHNICAL_FAILURE, "context-load", true),
  CHALLENGE_VERIFICATION(Codes.TECHNICAL_FAILURE, "challenge-verification", true),
  AUTHORIZATION_PROTECTION(Codes.TECHNICAL_FAILURE, "authorization-protection", true),
  SESSION_UPDATE(Codes.TECHNICAL_FAILURE, "session-update", true),
  EXPIRY_REFRESH(Codes.TECHNICAL_FAILURE, "expiry-refresh", true),
  PROTECTED_OPERATION(Codes.TECHNICAL_FAILURE, "protected-operation", true),
  IDEMPOTENCY("OTP_VERIFY_IDEMPOTENCY_FAILURE", "idempotency", true),
  LOCK_RELEASE(Codes.TECHNICAL_FAILURE, "lock-release", false);

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

  private static final class Codes {
    private static final String TECHNICAL_FAILURE = "OTP_VERIFY_TECHNICAL_FAILURE";

    private Codes() {}
  }
}
