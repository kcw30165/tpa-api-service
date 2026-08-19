package com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.details;

public final class ForgotPasswordDetailsDownstreamException extends RuntimeException {
  public enum Reason {
    UNEXPECTED_ERROR,
    EMPTY_DATA,
    AMBIGUOUS_DATA,
    CONTACT_MISMATCH,
    MALFORMED_DATA
  }

  private final Reason reason;

  public ForgotPasswordDetailsDownstreamException(Reason reason) {
    super("TRPSearchRegedMember contract failure: " + reason);
    this.reason = reason;
  }

  public Reason reason() {
    return reason;
  }
}
