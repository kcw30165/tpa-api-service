package com.bct.ngtpa.apiservice.adapter.out.apim.accountbalance.dto;

import com.fasterxml.jackson.annotation.*;

@JsonPropertyOrder({"login-user-id", "login-user-role", "policy-no", "cert-no", "ref-date", "curr"})
public record TrpGetMemberAccBalApimRequest(
    @JsonProperty("login-user-id") String loginUserId,
    @JsonProperty("login-user-role") String loginUserRole,
    @JsonProperty("policy-no") String policyNo,
    @JsonProperty("cert-no") Integer certNo,
    @JsonProperty("ref-date") String referenceDate,
    @JsonProperty("curr") String currency) {
  public TrpGetMemberAccBalApimRequest {
    currency = currency == null ? "" : currency;
  }
}
