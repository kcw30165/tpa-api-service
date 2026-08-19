package com.bct.ngtpa.apiservice.application.dto.auth.login.account;

import java.util.List;
import java.util.Objects;

public record LoginAccountAuthorizationResult(
    LoginAccountAuthorizationStatus status, List<LoginAccountRecord> accounts) {
  public LoginAccountAuthorizationResult {
    Objects.requireNonNull(status);
    accounts = accounts == null ? List.of() : List.copyOf(accounts);
    if (status == LoginAccountAuthorizationStatus.AVAILABLE && accounts.isEmpty())
      throw new IllegalArgumentException("available result requires accounts");
    if (status == LoginAccountAuthorizationStatus.UNAVAILABLE && !accounts.isEmpty())
      throw new IllegalArgumentException("unavailable result cannot carry accounts");
  }

  public static LoginAccountAuthorizationResult available(List<LoginAccountRecord> accounts) {
    return new LoginAccountAuthorizationResult(LoginAccountAuthorizationStatus.AVAILABLE, accounts);
  }

  public static LoginAccountAuthorizationResult unavailable() {
    return new LoginAccountAuthorizationResult(
        LoginAccountAuthorizationStatus.UNAVAILABLE, List.of());
  }

  @Override
  public String toString() {
    return "LoginAccountAuthorizationResult[status="
        + status
        + ", accountCount="
        + accounts.size()
        + "]";
  }
}
