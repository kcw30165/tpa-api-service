package com.bct.ngtpa.apiservice.application.service.accountbalance;

import com.bct.ngtpa.apiservice.application.dto.accountbalance.normalized.*;
import com.bct.ngtpa.apiservice.application.dto.accountbalance.raw.*;
import com.bct.ngtpa.apiservice.application.exception.AccountBalanceRetrievalException;
import com.bct.ngtpa.apiservice.application.exception.AccountBalanceRetrievalException.Collection;
import com.bct.ngtpa.apiservice.application.exception.AccountBalanceRetrievalException.Field;
import com.bct.ngtpa.apiservice.application.exception.AccountBalanceRetrievalException.NormalizationFailure;
import com.bct.ngtpa.apiservice.application.exception.AccountBalanceRetrievalException.ValidationReason;
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
    int sourceIndex = 0;
    for (RawAccountBalanceDisplaySource source : rawData.displaySources()) {
      if (source == null) throw systemFailure();
      var key =
          new DisplaySourceKey(
              required(source.displaySource(), Collection.DISP_SRC, sourceIndex, Field.DISP_SRC),
              required(
                  source.subDisplaySource(), Collection.DISP_SRC, sourceIndex, Field.SUB_DISP_SRC));
      if (displaySources.putIfAbsent(key, source) != null) throw systemFailure();
      sourceIndex++;
    }
    Map<FundKey, RawAccountBalanceFundDetail> funds = new LinkedHashMap<>();
    int fundIndex = 0;
    for (RawAccountBalanceFundDetail fund : rawData.fundDetails()) {
      if (fund == null) throw systemFailure();
      var key =
          new FundKey(required(fund.fundCode(), Collection.FUND_DTL, fundIndex, Field.FUND_CODE));
      if (funds.putIfAbsent(key, fund) != null) throw systemFailure();
      fundIndex++;
    }
    Map<HoldingKey, RawAccountBalanceHolding> holdings = new LinkedHashMap<>();
    int holdingIndex = 0;
    for (RawAccountBalanceHolding holding : rawData.holdings()) {
      if (holding == null) throw systemFailure();
      String displaySource =
          required(holding.displaySource(), Collection.ACC_BAL, holdingIndex, Field.DISP_SRC);
      String subDisplaySource =
          required(
              holding.subDisplaySource(), Collection.ACC_BAL, holdingIndex, Field.SUB_DISP_SRC);
      String fundCode =
          required(holding.fundCode(), Collection.ACC_BAL, holdingIndex, Field.FUND_CODE);
      var displayKey = new DisplaySourceKey(displaySource, subDisplaySource);
      var fundKey = new FundKey(fundCode);
      if (!displaySources.containsKey(displayKey) || !funds.containsKey(fundKey))
        throw systemFailure();
      var holdingKey = new HoldingKey(displaySource, subDisplaySource, fundCode);
      if (holdings.putIfAbsent(holdingKey, holding) != null) throw systemFailure();
      holdingIndex++;
    }
    return new NormalizedAccountBalanceData(
        rawData.displayOptions(), rawData.currency(), displaySources, funds, holdings);
  }

  private static String required(String value, Collection collection, int rowIndex, Field field) {
    ValidationReason reason = null;
    if (value == null) reason = ValidationReason.NULL;
    else if (value.isEmpty()) reason = ValidationReason.EMPTY;
    else if (value.trim().isEmpty()) reason = ValidationReason.WHITESPACE_ONLY;
    if (reason != null) {
      throw AccountBalanceRetrievalException.normalizationFailure(
          new NormalizationFailure(collection, rowIndex, field, reason));
    }
    return value;
  }

  private static AccountBalanceRetrievalException systemFailure() {
    return AccountBalanceRetrievalException.systemFailure();
  }
}
