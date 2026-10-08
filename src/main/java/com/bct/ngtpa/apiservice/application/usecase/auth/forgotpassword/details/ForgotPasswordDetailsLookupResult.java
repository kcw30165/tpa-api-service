package com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.details;

public record ForgotPasswordDetailsLookupResult(
    ForgotPasswordDetailsLookupStatus status,
    String accountEnv,
    String trustCode,
    String schemeName,
    String schemeNameChi) {
  public ForgotPasswordDetailsLookupResult(ForgotPasswordDetailsLookupStatus status) {
    this(status, null, null, null, null);
  }

  /**
   * Compatibility constructor for existing call sites predating the addition of {@code
   * trustCode}/{@code schemeName}/{@code schemeNameChi} (see
   * docs/design/10-template-service/template-service-implementation-plan.md Appendix E.3 and
   * E.9, II-4.b0). All three new fields default to {@code null}.
   */
  public ForgotPasswordDetailsLookupResult(
      ForgotPasswordDetailsLookupStatus status, String accountEnv) {
    this(status, accountEnv, null, null, null);
  }
}
