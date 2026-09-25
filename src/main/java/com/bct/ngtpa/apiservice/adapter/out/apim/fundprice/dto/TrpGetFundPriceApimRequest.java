package com.bct.ngtpa.apiservice.adapter.out.apim.fundprice.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

/** Downstream wire request for TRPGetFundPrice. Raw downstream field names confined here. */
@JsonPropertyOrder({"policy-no", "from-date", "to-date"})
public record TrpGetFundPriceApimRequest(
    @JsonProperty("policy-no") String policyNo,
    @JsonProperty("from-date") String fromDate,
    @JsonProperty("to-date") String toDate) {}
