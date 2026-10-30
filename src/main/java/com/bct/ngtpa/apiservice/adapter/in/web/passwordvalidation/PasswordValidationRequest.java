package com.bct.ngtpa.apiservice.adapter.in.web.passwordvalidation;

import com.fasterxml.jackson.databind.JsonNode;

/** Transport contract only; no endpoint is exposed by this type. */
public record PasswordValidationRequest(
    String password, String purpose, JsonNode operationPayload) {
  @Override
  public String toString() {
    return "PasswordValidationRequest[password=<redacted>, purpose=<redacted>, "
        + "operationPayload=<redacted>]";
  }
}
