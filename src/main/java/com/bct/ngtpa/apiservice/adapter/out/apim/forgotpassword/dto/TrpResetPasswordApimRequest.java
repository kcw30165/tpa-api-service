package com.bct.ngtpa.apiservice.adapter.out.apim.forgotpassword.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record TrpResetPasswordApimRequest(
    @JsonProperty("cust-id") String customerId, @JsonProperty("password") String password) {
  @Override
  public String toString() {
    return "TrpResetPasswordApimRequest[customerId=<redacted>, password=<redacted>]";
  }
}
