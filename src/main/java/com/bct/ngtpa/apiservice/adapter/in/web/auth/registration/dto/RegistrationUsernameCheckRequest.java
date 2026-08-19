package com.bct.ngtpa.apiservice.adapter.in.web.auth.registration.dto;

public record RegistrationUsernameCheckRequest(
    RegistrationSelectedTrustRequest selectedTrust, String username) {
  @Override
  public String toString() {
    return "RegistrationUsernameCheckRequest[selectedTrust="
        + selectedTrust
        + ", username=<redacted>]";
  }
}
