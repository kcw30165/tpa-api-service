package com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.detailsotp;

/** Signals an expected Details/OTP flow stop while preserving the existing failure response. */
public final class ForgotPasswordDetailsOtpExpectedFailureException extends RuntimeException {
  ForgotPasswordDetailsOtpExpectedFailureException() {
    super(null, null, false, false);
  }
}
