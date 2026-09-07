package com.bct.ngtpa.apiservice.adapter.out.apim.accountgainloss.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;

@JsonIgnoreProperties(ignoreUnknown = true)
public record TrpGetInvestGlFund(
    @JsonProperty("FundCode") String fundCode,
    @JsonProperty("fund-name") String fundName,
    @JsonProperty("chin-fund-name") String chineseFundName,
    @JsonProperty("fund-seq") Integer fundSequence,
    @JsonProperty("GL") BigDecimal gainLoss) {}
