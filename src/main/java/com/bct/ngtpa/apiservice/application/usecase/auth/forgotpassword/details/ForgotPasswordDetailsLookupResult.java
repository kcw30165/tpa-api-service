package com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.details;

public record ForgotPasswordDetailsLookupResult(
    ForgotPasswordDetailsLookupStatus status,
    String trustCode,
    String schemeName,
    String schemeNameChi) {
  /** The lookup found no matching registered member: no trust or scheme fields. */
  public static ForgotPasswordDetailsLookupResult notFound() {
    return new ForgotPasswordDetailsLookupResult(
        ForgotPasswordDetailsLookupStatus.NOT_FOUND, null, null, null);
  }
}
