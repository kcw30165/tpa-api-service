package com.bct.ngtpa.apiservice.adapter.out.apim.accountgainloss.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record TrpGetInvestGlApimRequest(
    @JsonProperty("login-user-id") String loginUserId,
    @JsonProperty("login-user-role") String loginUserRole,
    @JsonProperty("policy-no") String policyNo,
    @JsonProperty("cert-no") String certNo,
    @JsonProperty("ref-date") String referenceDate,
    @JsonProperty("mode") String mode) {}
