package com.bct.ngtpa.apiservice.adapter.out.apim.accountbalance.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AccountBalanceHolding(
    @JsonProperty("disp-src") String displaySource,
    @JsonProperty("sub-disp-src") String subDisplaySource,
    @JsonProperty("fund-code") String fundCode,
    @JsonProperty("unit") BigDecimal unit,
    @JsonProperty("nav") BigDecimal netAssetValue,
    @JsonProperty("vested-bal") BigDecimal vestedBalance,
    @JsonProperty("transit-amt") BigDecimal transitAmount,
    @JsonProperty("percentage") BigDecimal percentage) {

  /**
   * Compatibility constructor for callers predating the downstream percentage field.
   *
   * <p>TAS supplies {@code percentage} inside {@code accBal}, at display-source granularity. It is
   * absent until that downstream change is deployed, and absence is represented as null rather than
   * zero so an unreported share is never mistaken for a real zero share.
   */
  public AccountBalanceHolding(
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
