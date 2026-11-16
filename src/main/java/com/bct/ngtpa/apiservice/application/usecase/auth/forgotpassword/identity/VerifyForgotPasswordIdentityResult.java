package com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.identity;

public record VerifyForgotPasswordIdentityResult(
    VerifyForgotPasswordIdentityStatus status, boolean incidentPublished) {
  public static VerifyForgotPasswordIdentityResult of(VerifyForgotPasswordIdentityStatus status) {
    return new VerifyForgotPasswordIdentityResult(status, false);
  }

  public static VerifyForgotPasswordIdentityResult incidentPublished(
      VerifyForgotPasswordIdentityStatus status) {
    return new VerifyForgotPasswordIdentityResult(status, true);
  }
}
