package com.bct.ngtpa.apiservice.adapter.out.apim.registration.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Adapter-only DTO for TRPChkCustId. Field names are intentionally isolated in the APIM adapter
 * because the final TAS contract remains boundary-sensitive.
 */
public record TrpChkCustIdApimRequest(@JsonProperty("cust-id") String customerId) {
  @Override
  public String toString() {
    return "TrpChkCustIdApimRequest[customerId=<redacted>]";
  }
}
