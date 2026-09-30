package com.bct.ngtpa.apiservice.adapter.out.apim.fundprice.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

/** Downstream wire request for TRPGetFundPriceMode. Raw downstream field names confined here. */
@JsonPropertyOrder({"policy-no", "ref-date"})
public record TrpGetFundPriceModeApimRequest(
    @JsonProperty("policy-no") String policyNo, @JsonProperty("ref-date") String referenceDate) {}
