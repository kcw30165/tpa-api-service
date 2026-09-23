package com.bct.ngtpa.apiservice.adapter.out.apim.fundprice.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/** Downstream wire response item for TRPGetFundPriceMode. Raw downstream field names confined here. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TrpGetFundPriceModeApimDataItem(
    @JsonProperty("mode") String mode,
    @JsonProperty("fund-price-type") String fundPriceType,
    @JsonProperty("available-fund-price-dates") List<String> availableFundPriceDates,
    @JsonProperty("maximum-fund-price-history-months") int maximumFundPriceHistoryMonths) {}
