package com.bct.ngtpa.apiservice.adapter.out.apim.passwordvalidation.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record TrpPwdValidationRequest(
    @JsonProperty("cust-id") String customerId, @JsonProperty("password") String password) {
  @Override
  public String toString() {
    return "TrpPwdValidationRequest[customerId=<redacted>, password=<redacted>]";
  }
}
