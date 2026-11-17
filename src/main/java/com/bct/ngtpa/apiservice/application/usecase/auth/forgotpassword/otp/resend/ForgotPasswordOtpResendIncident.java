package com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.otp.resend;

/** Fixed, value-free incident metadata classified by the protected resend operation. */
public enum ForgotPasswordOtpResendIncident {
  PENDING_REFERENCE_GENERATION(InternalCodes.TECHNICAL_FAILURE, "pending-reference", true),
  LOCK_ACQUISITION(InternalCodes.TECHNICAL_FAILURE, "lock-acquisition", true),
  SESSION_READ(InternalCodes.TECHNICAL_FAILURE, "session-read", true),
  CONTEXT_UNPROTECT(InternalCodes.TECHNICAL_FAILURE, "context-unprotect", true),
  CHALLENGE_READ(InternalCodes.TECHNICAL_FAILURE, "challenge-read", true),
  FOUNDATION(InternalCodes.TECHNICAL_FAILURE, "foundation", true),
  TEMPLATE_RESOLUTION(InternalCodes.TECHNICAL_FAILURE, "template-resolution", true),
  CHALLENGE_CREATION(InternalCodes.TECHNICAL_FAILURE, "challenge-creation", true),
  CONTEXT_PROTECTION(InternalCodes.TECHNICAL_FAILURE, "context-protection", true),
  SESSION_MUTATION(InternalCodes.TECHNICAL_FAILURE, "session-mutation", true),
  CHALLENGE_MUTATION(InternalCodes.TECHNICAL_FAILURE, "challenge-mutation", true),
  COMMIT_STATE(InternalCodes.TECHNICAL_FAILURE, "commit-state", true),
  MESSAGE_DELIVERY(InternalCodes.TECHNICAL_FAILURE, "message-delivery", true),
  UNKNOWN_DELIVERY("OTP_RESEND_UNKNOWN_DELIVERY_OUTCOME", "message-delivery", true),
  MALFORMED_RESEND_COUNT("OTP_RESEND_COUNTER_MALFORMED", "resend-count", false),
  IDEMPOTENCY("OTP_RESEND_IDEMPOTENCY_FAILURE", "idempotency", true),
  PROTECTED_OPERATION(InternalCodes.TECHNICAL_FAILURE, "protected-operation", true);

  private static final class InternalCodes {
    private static final String TECHNICAL_FAILURE = "OTP_RESEND_TECHNICAL_FAILURE";
  }

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
