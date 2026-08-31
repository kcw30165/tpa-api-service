package com.bct.ngtpa.apiservice.adapter.out.apim.accountbalance.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record AccountBalanceDisplaySource(
    @JsonProperty("disp-src") String displaySource,
    @JsonProperty("src-desc") String sourceDescription,
    @JsonProperty("src-chin-desc") String sourceChineseDescription,
    @JsonProperty("sub-disp-src") String subDisplaySource,
    @JsonProperty("sub-src-desc") String subSourceDescription,
    @JsonProperty("sub-src-chin-desc") String subSourceChineseDescription,
    @JsonProperty("seq") Integer sequence) {}
