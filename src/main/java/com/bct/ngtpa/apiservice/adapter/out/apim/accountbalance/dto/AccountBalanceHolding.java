package com.bct.ngtpa.apiservice.adapter.out.apim.accountbalance.dto;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
public record AccountBalanceHolding(
 @JsonProperty("disp-src") String displaySource, @JsonProperty("sub-disp-src") String subDisplaySource,
 @JsonProperty("fund-code") String fundCode, @JsonProperty("unit") BigDecimal unit,
 @JsonProperty("nav") BigDecimal netAssetValue, @JsonProperty("vested-bal") BigDecimal vestedBalance,
 @JsonProperty("transit-amt") BigDecimal transitAmount) {}
