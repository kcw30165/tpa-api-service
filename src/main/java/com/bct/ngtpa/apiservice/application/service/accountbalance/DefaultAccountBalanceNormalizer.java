package com.bct.ngtpa.apiservice.application.service.accountbalance;

import com.bct.ngtpa.apiservice.application.dto.accountbalance.normalized.*;
import com.bct.ngtpa.apiservice.application.dto.accountbalance.raw.*;
import com.bct.ngtpa.apiservice.application.exception.AccountBalanceRetrievalException;
import com.bct.ngtpa.apiservice.application.port.in.AccountBalanceNormalizer;
import java.util.LinkedHashMap;
import java.util.Map;

public final class DefaultAccountBalanceNormalizer implements AccountBalanceNormalizer {
  @Override
  public NormalizedAccountBalanceData normalize(RawAccountBalanceData rawData) {
    if (rawData == null) {
      throw systemFailure();
    }
    Map<DisplaySourceKey, RawAccountBalanceDisplaySource> displaySources = new LinkedHashMap<>();
    for (RawAccountBalanceDisplaySource source : rawData.displaySources()) {
      if (source == null) throw systemFailure();
      var key = new DisplaySourceKey(required(source.displaySource()), required(source.subDisplaySource()));
      if (displaySources.putIfAbsent(key, source) != null) throw systemFailure();
    }
    Map<FundKey, RawAccountBalanceFundDetail> funds = new LinkedHashMap<>();
    for (RawAccountBalanceFundDetail fund : rawData.fundDetails()) {
      if (fund == null) throw systemFailure();
      var key = new FundKey(required(fund.fundCode()));
      if (funds.putIfAbsent(key, fund) != null) throw systemFailure();
    }
    Map<HoldingKey, RawAccountBalanceHolding> holdings = new LinkedHashMap<>();
    for (RawAccountBalanceHolding holding : rawData.holdings()) {
      if (holding == null) throw systemFailure();
      String displaySource = required(holding.displaySource());
      String subDisplaySource = required(holding.subDisplaySource());
      String fundCode = required(holding.fundCode());
      var displayKey = new DisplaySourceKey(displaySource, subDisplaySource);
      var fundKey = new FundKey(fundCode);
      if (!displaySources.containsKey(displayKey) || !funds.containsKey(fundKey)) throw systemFailure();
      var holdingKey = new HoldingKey(displaySource, subDisplaySource, fundCode);
      if (holdings.putIfAbsent(holdingKey, holding) != null) throw systemFailure();
    }
    return new NormalizedAccountBalanceData(rawData.displayOptions(), rawData.currency(),
        displaySources, funds, holdings);
  }

  private static String required(String value) {
    if (value == null || value.trim().isEmpty()) throw systemFailure();
    return value;
  }

  private static AccountBalanceRetrievalException systemFailure() {
    return AccountBalanceRetrievalException.systemFailure();
  }
}
