package com.bct.ngtpa.apiservice.adapter.out.apim.registration.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record TrpRegMemberApimRequest(
    @JsonProperty("env") String environment,
    @JsonProperty("trust-code") String trustCode,
    @JsonProperty("cust-id") String customerId,
    @JsonProperty("user-id") String userId,
    @JsonProperty("password") String password,
    @JsonProperty("dob") String dateOfBirth,
    @JsonProperty("mobile-number") String mobileNumber,
    @JsonProperty("email") String email) {
  @Override
  public String toString() {
    return "TrpRegMemberApimRequest[environment=<redacted>, trustCode=<redacted>, "
        + "customerId=<redacted>, userId=<redacted>, password=<redacted>, "
        + "dateOfBirth=<redacted>, mobileNumber=<redacted>, email=<redacted>]";
  }
}
