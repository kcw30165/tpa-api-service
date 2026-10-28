package com.bct.ngtpa.apiservice.adapter.out.apim.passwordvalidation.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;

/** A JSON node prevents Jackson's scalar coercion from accepting a non-boolean success value. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TrpPwdValidationDataItem(@JsonProperty("success") JsonNode success) {
  @Override
  public String toString() {
    return "TrpPwdValidationDataItem[success=<redacted>]";
  }
}
