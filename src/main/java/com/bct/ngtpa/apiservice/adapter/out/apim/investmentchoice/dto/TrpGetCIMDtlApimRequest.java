package com.bct.ngtpa.apiservice.adapter.out.apim.investmentchoice.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonPropertyOrder({"policy-no", "cert-no", "ref-date"})
public record TrpGetCIMDtlApimRequest(
    @JsonProperty("policy-no") String policyNo,
    @JsonProperty("cert-no") String certNo,
    @JsonProperty("ref-date") String refDate) {}
