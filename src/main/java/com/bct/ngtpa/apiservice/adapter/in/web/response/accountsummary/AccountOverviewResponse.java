package com.bct.ngtpa.apiservice.adapter.in.web.response.accountsummary;

import java.util.List;

public record AccountOverviewResponse(
    AccountOverviewDisplayOptionsResponse displayOptions,
    List<AccountOverviewDisplayAccountResponse> displayAccounts,
    AccountOverviewTotalsResponse accountTotals) {
  public AccountOverviewResponse {
    displayAccounts = displayAccounts == null ? List.of() : List.copyOf(displayAccounts);
  }
}
