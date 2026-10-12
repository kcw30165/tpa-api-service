package com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.detailsotp;

import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.details.ForgotPasswordDetailsStatus;

/**
 * Outcome of the details check made under the session mutation lock. A verified outcome carries the
 * trust and scheme fields the member lookup returned for the matched account, because the protected
 * context created at START holds none of them and nothing else on the live path writes them.
 */
public record ForgotPasswordUnderLockDetailsVerification(
    ForgotPasswordDetailsStatus status,
    String trustCode,
    String schemeName,
    String schemeNameChi) {

  public static ForgotPasswordUnderLockDetailsVerification of(ForgotPasswordDetailsStatus status) {
    return new ForgotPasswordUnderLockDetailsVerification(status, null, null, null);
  }
}
