package com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.details;

public record ForgotPasswordDetailsLookupResult(
    ForgotPasswordDetailsLookupStatus status,
    String trustCode,
    String schemeName,
    String schemeNameChi) {
  public ForgotPasswordDetailsLookupResult(ForgotPasswordDetailsLookupStatus status) {
    this(status, null, null, null);
  }
}
