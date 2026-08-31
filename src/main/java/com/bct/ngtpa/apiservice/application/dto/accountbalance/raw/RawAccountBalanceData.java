package com.bct.ngtpa.apiservice.application.dto.accountbalance.raw;

import java.util.List;

public record RawAccountBalanceData(
    RawAccountBalanceDisplayOptions displayOptions,
    RawAccountBalanceCurrency currency,
    List<RawAccountBalanceDisplaySource> displaySources,
    List<RawAccountBalanceFundDetail> fundDetails,
    List<RawAccountBalanceHolding> holdings) {
  public RawAccountBalanceData {
    displaySources = displaySources == null ? List.of() : List.copyOf(displaySources);
    fundDetails = fundDetails == null ? List.of() : List.copyOf(fundDetails);
    holdings = holdings == null ? List.of() : List.copyOf(holdings);
  }
}
