package com.bct.ngtpa.apiservice.application.dto.accountbalance.raw;

import java.math.BigDecimal;

public record RawAccountBalanceHolding(
    String displaySource,
    String subDisplaySource,
    String fundCode,
    BigDecimal unit,
    BigDecimal netAssetValue,
    BigDecimal vestedBalance,
    BigDecimal transitAmount,
    BigDecimal percentage) {

  /** Compatibility constructor for callers predating the downstream percentage field. */
  public RawAccountBalanceHolding(
      String displaySource,
      String subDisplaySource,
      String fundCode,
      BigDecimal unit,
      BigDecimal netAssetValue,
      BigDecimal vestedBalance,
      BigDecimal transitAmount) {
    this(
        displaySource,
        subDisplaySource,
        fundCode,
        unit,
        netAssetValue,
        vestedBalance,
        transitAmount,
        null);
  }
}
