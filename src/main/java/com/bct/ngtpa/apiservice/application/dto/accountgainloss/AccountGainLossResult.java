package com.bct.ngtpa.apiservice.application.dto.accountgainloss;

import java.util.List;

public record AccountGainLossResult(
    AccountGainLossPeriod period,
    String asOfDate,
    String totalGainLoss,
    List<AccountGainLossFund> funds) {
  public AccountGainLossResult {
    funds = List.copyOf(funds);
  }
}
