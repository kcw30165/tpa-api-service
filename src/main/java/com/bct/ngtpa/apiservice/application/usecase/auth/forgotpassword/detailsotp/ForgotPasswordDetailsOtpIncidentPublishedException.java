package com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.detailsotp;

/** Signals that this request's Details/OTP technical incident was already published. */
public final class ForgotPasswordDetailsOtpIncidentPublishedException extends RuntimeException {
  ForgotPasswordDetailsOtpIncidentPublishedException() {
    super(null, null, false, false);
  }
}
