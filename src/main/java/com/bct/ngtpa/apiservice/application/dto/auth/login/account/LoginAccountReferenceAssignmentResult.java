package com.bct.ngtpa.apiservice.application.dto.auth.login.account;

import java.util.List;

public record LoginAccountReferenceAssignmentResult(List<LoginSessionAccount> accounts) {
  public LoginAccountReferenceAssignmentResult {
    accounts = accounts == null ? List.of() : List.copyOf(accounts);
    if (accounts.isEmpty()) throw new IllegalArgumentException("At least one account is required");
  }

  @Override
  public String toString() {
    return "LoginAccountReferenceAssignmentResult[accountCount=" + accounts.size() + "]";
  }
}
