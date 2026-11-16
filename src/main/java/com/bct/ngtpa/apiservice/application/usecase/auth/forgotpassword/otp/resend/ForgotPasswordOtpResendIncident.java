package com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.otp.resend;

/** Fixed, value-free incident metadata classified by the protected resend operation. */
public enum ForgotPasswordOtpResendIncident {
  PENDING_REFERENCE_GENERATION("OTP_RESEND_TECHNICAL_FAILURE", "pending-reference", true),
  LOCK_ACQUISITION("OTP_RESEND_TECHNICAL_FAILURE", "lock-acquisition", true),
  SESSION_READ("OTP_RESEND_TECHNICAL_FAILURE", "session-read", true),
  CONTEXT_UNPROTECT("OTP_RESEND_TECHNICAL_FAILURE", "context-unprotect", true),
  CHALLENGE_READ("OTP_RESEND_TECHNICAL_FAILURE", "challenge-read", true),
  FOUNDATION("OTP_RESEND_TECHNICAL_FAILURE", "foundation", true),
  TEMPLATE_RESOLUTION("OTP_RESEND_TECHNICAL_FAILURE", "template-resolution", true),
  CHALLENGE_CREATION("OTP_RESEND_TECHNICAL_FAILURE", "challenge-creation", true),
  CONTEXT_PROTECTION("OTP_RESEND_TECHNICAL_FAILURE", "context-protection", true),
  SESSION_MUTATION("OTP_RESEND_TECHNICAL_FAILURE", "session-mutation", true),
  CHALLENGE_MUTATION("OTP_RESEND_TECHNICAL_FAILURE", "challenge-mutation", true),
  COMMIT_STATE("OTP_RESEND_TECHNICAL_FAILURE", "commit-state", true),
  MESSAGE_DELIVERY("OTP_RESEND_TECHNICAL_FAILURE", "message-delivery", true),
  UNKNOWN_DELIVERY("OTP_RESEND_UNKNOWN_DELIVERY_OUTCOME", "message-delivery", true),
  MALFORMED_RESEND_COUNT("OTP_RESEND_COUNTER_MALFORMED", "resend-count", false),
  IDEMPOTENCY("OTP_RESEND_IDEMPOTENCY_FAILURE", "idempotency", true),
  PROTECTED_OPERATION("OTP_RESEND_TECHNICAL_FAILURE", "protected-operation", true);

  private final String internalCode;
  private final String stage;
  private final boolean terminal;

  ForgotPasswordOtpResendIncident(String internalCode, String stage, boolean terminal) {
    this.internalCode = internalCode;
    this.stage = stage;
    this.terminal = terminal;
  }

  public String internalCode() {
    return internalCode;
  }

  public String stage() {
    return stage;
  }

  public boolean terminal() {
    return terminal;
  }
}
