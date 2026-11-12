package com.bct.ngtpa.apiservice.adapter.out.apim.vestingscale.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record TrpGetVestingScaleApimRequest(
    @JsonProperty("login-user-id") String loginUserId,
    @JsonProperty("login-user-role") String loginUserRole,
    @JsonProperty("policy-no") String policyNo,
    @JsonProperty("cert-no") String certNo) {}
