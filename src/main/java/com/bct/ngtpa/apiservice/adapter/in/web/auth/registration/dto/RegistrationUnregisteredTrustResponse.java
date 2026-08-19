package com.bct.ngtpa.apiservice.adapter.in.web.auth.registration.dto;

public record RegistrationUnregisteredTrustResponse(String accountEnv, String trustCode) {
  @Override
  public String toString() {
    return "RegistrationUnregisteredTrustResponse[accountEnv="
        + accountEnv
        + ", trustCode="
        + trustCode
        + "]";
  }
}
