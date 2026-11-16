package com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.start;

/** Signals that the Start protected operation already emitted its terminal technical incident. */
public final class ForgotPasswordStartTechnicalFailure extends RuntimeException {
  private static final long serialVersionUID = 1L;

  public ForgotPasswordStartTechnicalFailure() {
    super("Forgot Password Start failed");
  }
}
