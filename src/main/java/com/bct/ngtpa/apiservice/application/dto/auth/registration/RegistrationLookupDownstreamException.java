package com.bct.ngtpa.apiservice.application.dto.auth.registration;

/**
 * Signals a structurally readable TRPSearchMember response that violates the agreed lookup
 * contract. Sensitive request/response values are deliberately excluded from the exception message
 * and state.
 */
public final class RegistrationLookupDownstreamException extends RuntimeException {
  private final String downstreamErrorCode;
  private final Reason reason;

  public RegistrationLookupDownstreamException(Reason reason, String downstreamErrorCode) {
    super("TRPSearchMember contract failure: " + reason);
    this.reason = java.util.Objects.requireNonNull(reason, "reason must not be null");
    this.downstreamErrorCode =
        downstreamErrorCode == null || downstreamErrorCode.isBlank()
            ? null
            : downstreamErrorCode.trim();
  }

  public Reason reason() {
    return reason;
  }

  public String downstreamErrorCode() {
    return downstreamErrorCode;
  }

  public enum Reason {
    UNEXPECTED_ERROR,
    EMPTY_DATA,
    CONTACT_MISMATCH,
    INVALID_ACCOUNT_DATA
  }
}
