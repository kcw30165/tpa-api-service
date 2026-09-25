package com.bct.ngtpa.apiservice.adapter.out.apim.fundprice.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.util.List;

/**
 * Downstream wire response item for TRPGetFundPrice. Raw downstream field names confined here.
 *
 * <p>Confirmed byte-exact from fund-price-apim-contract.md's Operation 2 section: one wrapper item
 * containing {@code FundPrice} and {@code FundInfo} arrays -- NOT a flat item like {@code
 * TrpGetFundPriceModeApimDataItem}.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TrpGetFundPriceApimDataItem(
    @JsonProperty("FundPrice") List<FundPriceEntry> fundPrice,
    @JsonProperty("FundInfo") List<FundInfoEntry> fundInfo) {

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record FundPriceEntry(
      @JsonProperty("fund-code") String fundCode,
      @JsonProperty("price-date") String priceDate,
      @JsonProperty("bid-price") BigDecimal bidPrice,
      @JsonProperty("offer-price") BigDecimal offerPrice) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record FundInfoEntry(
      @JsonProperty("fund-code") String fundCode,
      @JsonProperty("fund-name") String fundName,
      @JsonProperty("chin-fund-name") String chineseFundName,
      @JsonProperty("fund-seq") int fundSeq,
      @JsonProperty("fund-curr") String fundCurrency,
      @JsonProperty("risk-level") String riskLevel) {}
}
