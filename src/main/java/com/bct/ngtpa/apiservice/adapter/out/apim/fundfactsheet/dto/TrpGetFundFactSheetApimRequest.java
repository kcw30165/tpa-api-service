package com.bct.ngtpa.apiservice.adapter.out.apim.fundfactsheet.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

/** Downstream wire request for TRPGetFundFactSheet. Raw downstream field names stay here. */
@JsonPropertyOrder({"policy-no", "ref-date"})
public record TrpGetFundFactSheetApimRequest(
    @JsonProperty("policy-no") String policyNo, @JsonProperty("ref-date") String referenceDate) {}
