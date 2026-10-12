package com.bct.ngtpa.apiservice.adapter.out.apim.registration.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Adapter-only TRPChkCustId response.data[] item.
 *
 * <p>APIM returns isExist=true when the submitted customer id already exists. Keep that meaning
 * unchanged at the DTO boundary; availability is derived by the result mapper, not by mutating the
 * raw APIM value.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TrpChkCustIdApimDataItem(
    @JsonProperty("cust-id") String customerId,
    @JsonProperty("isExist") Boolean isExist,
    @JsonProperty("scheme-code") String schemeCode,
    @JsonProperty("user-id") String userId,
    @JsonProperty("id-type") String idType) {

  public boolean exists() {
    return Boolean.TRUE.equals(isExist);
  }

  @Override
  public String toString() {
    return "TrpChkCustIdApimDataItem[customerId=<redacted>, isExist="
        + isExist
        + ", schemeCode="
        + schemeCode
        + ", userId=<redacted>, idType="
        + idType
        + "]";
  }
}
