package com.bct.ngtpa.apiservice.adapter.out.apim.fundprice.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * Downstream wire response item for TRPGetFundPriceMode. Raw downstream field names confined here.
 *
 * <p>{@code maxRiskLevel} ("max-risk-level" on the wire) is a string holding a floating-point value
 * (e.g. "5", "0.5"), sibling of the other four fields, appended last to preserve the original field
 * order and every existing construction call site. Per fund-price-apim-contract.md, it is
 * deliberately NOT validated: a blank or absent value is legitimate and must not fail the mode
 * fetch, unlike mode/fund-price-type/ maximum-fund-price-history-months/available-fund-price-dates.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TrpGetFundPriceModeApimDataItem(
    @JsonProperty("mode") String mode,
    @JsonProperty("fund-price-type") String fundPriceType,
    @JsonProperty("available-fund-price-dates") List<String> availableFundPriceDates,
    @JsonProperty("maximum-fund-price-history-months") int maximumFundPriceHistoryMonths,
    @JsonProperty("max-risk-level") String maxRiskLevel) {}
