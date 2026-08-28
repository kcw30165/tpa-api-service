package com.bct.ngtpa.apiservice.application.dto.accountbalance.portfolio;

import java.util.List;

public record RawSubDisplayLevelDisplayAccount(
    String displayAccountId,
    Integer displaySequence,
    List<RawSubDisplayLevelSubDisplayAccount> subDisplayAccounts) {
  public RawSubDisplayLevelDisplayAccount {
    subDisplayAccounts = subDisplayAccounts == null ? List.of() : List.copyOf(subDisplayAccounts);
  }
}
