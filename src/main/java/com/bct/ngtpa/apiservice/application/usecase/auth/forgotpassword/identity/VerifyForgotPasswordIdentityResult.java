package com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.identity;

public record VerifyForgotPasswordIdentityResult(
    VerifyForgotPasswordIdentityStatus status, boolean incidentPublished) {
  public VerifyForgotPasswordIdentityResult(VerifyForgotPasswordIdentityStatus status) {
    this(status, false);
  }

  public static VerifyForgotPasswordIdentityResult of(VerifyForgotPasswordIdentityStatus status) {
    return new VerifyForgotPasswordIdentityResult(status);
  }

  public static VerifyForgotPasswordIdentityResult incidentPublished(
      VerifyForgotPasswordIdentityStatus status) {
    return new VerifyForgotPasswordIdentityResult(status, true);
  }
}
