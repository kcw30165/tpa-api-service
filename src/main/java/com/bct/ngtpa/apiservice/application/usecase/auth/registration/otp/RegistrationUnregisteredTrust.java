package com.bct.ngtpa.apiservice.application.usecase.auth.registration.otp;

public record RegistrationUnregisteredTrust(String accountEnv, String trustCode) {
  @Override
  public String toString() {
    return "RegistrationUnregisteredTrust[accountEnv="
        + accountEnv
        + ", trustCode="
        + trustCode
        + "]";
  }
}
