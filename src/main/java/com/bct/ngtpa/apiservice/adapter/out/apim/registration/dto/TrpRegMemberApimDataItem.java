package com.bct.ngtpa.apiservice.adapter.out.apim.registration.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record TrpRegMemberApimDataItem(
    @JsonProperty("success") Boolean success,
    @JsonProperty("cust-id") String customerId,
    @JsonProperty("create-date") String createDate,
    @JsonProperty("create-time") String createTime) {
  @Override
  public String toString() {
    return "TrpRegMemberApimDataItem[success="
        + success
        + ", customerId=<redacted>, createDate=<redacted>, createTime=<redacted>]";
  }
}
