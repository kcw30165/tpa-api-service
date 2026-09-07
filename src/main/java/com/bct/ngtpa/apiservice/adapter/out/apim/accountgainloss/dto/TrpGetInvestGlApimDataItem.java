package com.bct.ngtpa.apiservice.adapter.out.apim.accountgainloss.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDate;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record TrpGetInvestGlApimDataItem(
    @JsonProperty("pri-date") LocalDate priceDate,
    @JsonProperty("InvestGL") List<TrpGetInvestGlFund> funds) {}
