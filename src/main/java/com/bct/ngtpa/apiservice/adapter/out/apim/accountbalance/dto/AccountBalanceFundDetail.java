package com.bct.ngtpa.apiservice.adapter.out.apim.accountbalance.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AccountBalanceFundDetail(
    @JsonProperty("fund-code") String fundCode,
    @JsonProperty("fund-name") String fundName,
    @JsonProperty("chin-fund-name") String chineseFundName,
    @JsonProperty("fund-seq") Integer fundSequence,
    @JsonProperty("fund-price") BigDecimal fundPrice,
    @JsonProperty("fund-curr") String fundCurrency,
    @JsonProperty("exchange-rate") BigDecimal exchangeRate,
    @JsonProperty("fund-fact-sheet") String fundFactsheet,
    @JsonProperty("chin-fund-fact-sheet") String chineseFundFactsheet) {}
