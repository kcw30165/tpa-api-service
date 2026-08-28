package com.bct.ngtpa.apiservice.application.dto.accountbalance.normalized;

import com.bct.ngtpa.apiservice.application.dto.accountbalance.raw.*;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public record NormalizedAccountBalanceData(
    RawAccountBalanceDisplayOptions displayOptions,
    RawAccountBalanceCurrency currency,
    Map<DisplaySourceKey, RawAccountBalanceDisplaySource> displaySources,
    Map<FundKey, RawAccountBalanceFundDetail> funds,
    Map<HoldingKey, RawAccountBalanceHolding> holdings) {
  public NormalizedAccountBalanceData {
    Objects.requireNonNull(displaySources, "displaySources must not be null");
    Objects.requireNonNull(funds, "funds must not be null");
    Objects.requireNonNull(holdings, "holdings must not be null");
    displaySources = Collections.unmodifiableMap(new LinkedHashMap<>(displaySources));
    funds = Collections.unmodifiableMap(new LinkedHashMap<>(funds));
    holdings = Collections.unmodifiableMap(new LinkedHashMap<>(holdings));
  }
}
