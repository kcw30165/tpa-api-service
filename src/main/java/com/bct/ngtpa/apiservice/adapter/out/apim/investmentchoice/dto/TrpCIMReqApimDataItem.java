package com.bct.ngtpa.apiservice.adapter.out.apim.investmentchoice.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record TrpCIMReqApimDataItem(
    @JsonProperty("success") Boolean success,
    @JsonProperty("submit-date") String submitDate,
    @JsonProperty("submit-time") String submitTime,
    @JsonProperty("ref-no") String referenceNo) {}
