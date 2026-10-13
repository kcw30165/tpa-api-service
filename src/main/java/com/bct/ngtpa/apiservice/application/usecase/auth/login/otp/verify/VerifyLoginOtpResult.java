package com.bct.ngtpa.apiservice.application.usecase.auth.login.otp.verify;

import com.bct.ngtpa.apiservice.application.dto.auth.login.account.LoginSessionAccount;
import com.bct.ngtpa.apiservice.application.dto.session.authenticated.AuthenticatedSessionId;
import java.util.*;

public record VerifyLoginOtpResult(
    VerifyLoginOtpStatus status,
    Integer remainingVerifyAttempts,
    boolean clearAuthFlowCookie,
    List<LoginSessionAccount> accounts,
    AuthenticatedSessionId authenticatedSessionId,
    String authenticatedCsrfToken) {
  public VerifyLoginOtpResult {
    Objects.requireNonNull(status);
    accounts = accounts == null ? List.of() : List.copyOf(accounts);
    if (status == VerifyLoginOtpStatus.ACCOUNTS_READY
        && (accounts.isEmpty()
            || authenticatedSessionId == null
            || authenticatedCsrfToken == null
            || authenticatedCsrfToken.isBlank()))
      throw new IllegalArgumentException(
          "ACCOUNTS_READY requires accounts and authenticated credentials");
    if (status != VerifyLoginOtpStatus.ACCOUNTS_READY
        && (!accounts.isEmpty()
            || authenticatedSessionId != null
            || authenticatedCsrfToken != null))
      throw new IllegalArgumentException(
          "Failure result cannot carry accounts or authenticated credentials");
    authenticatedCsrfToken =
        status == VerifyLoginOtpStatus.ACCOUNTS_READY ? authenticatedCsrfToken.trim() : null;
  }

  @Override
  public String toString() {
    return "VerifyLoginOtpResult[status="
        + status
        + ", remainingVerifyAttempts="
        + remainingVerifyAttempts
        + ", clearAuthFlowCookie="
        + clearAuthFlowCookie
        + ", accountCount="
        + accounts.size()
        + ", authenticatedSessionId=<redacted>, authenticatedCsrfToken=<redacted>]";
  }
}
