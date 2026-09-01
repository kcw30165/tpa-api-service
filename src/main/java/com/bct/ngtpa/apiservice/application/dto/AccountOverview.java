package com.bct.ngtpa.apiservice.application.dto;

import java.util.List;

public record AccountOverview(
    AccountOverviewDisplayOptions displayOptions,
    List<AccountOverviewDisplayAccount> displayAccounts,
    AccountOverviewTotals accountTotals) {
  public AccountOverview {
    displayAccounts = displayAccounts == null ? List.of() : List.copyOf(displayAccounts);
  }
}
