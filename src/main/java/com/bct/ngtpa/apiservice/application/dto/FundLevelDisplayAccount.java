package com.bct.ngtpa.apiservice.application.dto;

import java.util.List;

public record FundLevelDisplayAccount(
    String displayAccountName,
    String displayAccountId,
    Integer displaySequence,
    List<FundLevelFund> funds) {
  public FundLevelDisplayAccount {
    funds = funds == null ? List.of() : List.copyOf(funds);
  }
}
