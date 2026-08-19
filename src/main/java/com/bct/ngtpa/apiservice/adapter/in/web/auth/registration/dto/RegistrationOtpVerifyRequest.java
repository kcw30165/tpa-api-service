package com.bct.ngtpa.apiservice.adapter.in.web.auth.registration.dto;

public record RegistrationOtpVerifyRequest(String code) {
  @Override
  public String toString() {
    return "RegistrationOtpVerifyRequest[code=<redacted>]";
  }
}
