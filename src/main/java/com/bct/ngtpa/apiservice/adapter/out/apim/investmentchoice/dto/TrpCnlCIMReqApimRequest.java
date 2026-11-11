package com.bct.ngtpa.apiservice.adapter.out.apim.investmentchoice.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonPropertyOrder({"login-user-id", "login-user-role", "policy-no", "cert-no", "ref-no"})
public record TrpCnlCIMReqApimRequest(
    @JsonProperty("login-user-id") String loginUserId,
    @JsonProperty("login-user-role") String loginUserRole,
    @JsonProperty("policy-no") String policyNo,
    @JsonProperty("cert-no") String certNo,
    @JsonProperty("ref-no") String referenceNo) {}
