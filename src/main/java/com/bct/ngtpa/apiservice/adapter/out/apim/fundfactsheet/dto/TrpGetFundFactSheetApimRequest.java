package com.bct.ngtpa.apiservice.adapter.out.apim.fundfactsheet.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

/**
 * Downstream wire request for TRPGetFundFactSheet. Raw downstream field names stay here. All three
 * values are JSON Strings and are sent exactly as given.
 */
@JsonPropertyOrder({"policy-no", "cert-no", "sch-type"})
public record TrpGetFundFactSheetApimRequest(
    @JsonProperty("policy-no") String policyNo,
    @JsonProperty("cert-no") String certNo,
    @JsonProperty("sch-type") String schemeType) {}
