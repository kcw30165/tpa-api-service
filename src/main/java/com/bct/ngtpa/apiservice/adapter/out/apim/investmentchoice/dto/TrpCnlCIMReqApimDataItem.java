package com.bct.ngtpa.apiservice.adapter.out.apim.investmentchoice.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record TrpCnlCIMReqApimDataItem(
    @JsonProperty("success") Boolean success, @JsonProperty("ref-no") String referenceNo) {}
