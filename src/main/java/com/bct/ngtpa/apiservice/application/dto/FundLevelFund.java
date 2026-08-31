package com.bct.ngtpa.apiservice.application.dto;

import java.util.List;

public record FundLevelFund(
    String fundCode,
    String fundName,
    String fundPrice,
    String unitBalance,
    String accountBalance,
    String factsheetUrl,
    String fxRate,
    List<FundLevelSubDisplayAccount> subDisplayAccounts) {
  public FundLevelFund {
    subDisplayAccounts = subDisplayAccounts == null ? List.of() : List.copyOf(subDisplayAccounts);
  }
}
