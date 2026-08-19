package com.bct.ngtpa.apiservice.adapter.in.web.auth.login.dto;

import java.util.List;

/** Public Login OTP verification data. The CSRF value is returned once after authentication. */
public record LoginOtpVerifyDataResponse(
    String csrfToken,
    Integer remainingVerifyAttempts,
    List<LoginOtpActionResponse> actions,
    List<LoginOtpAccountResponse> accounts) {

  public LoginOtpVerifyDataResponse {
    csrfToken = normalize(csrfToken);
    actions = actions == null ? List.of() : List.copyOf(actions);
    accounts = accounts == null ? List.of() : List.copyOf(accounts);
  }

  @Override
  public String toString() {
    return "LoginOtpVerifyDataResponse[csrfToken=<redacted>, remainingVerifyAttempts="
        + remainingVerifyAttempts
        + ", actions="
        + actions
        + ", accountCount="
        + accounts.size()
        + "]";
  }

  private static String normalize(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }
}
