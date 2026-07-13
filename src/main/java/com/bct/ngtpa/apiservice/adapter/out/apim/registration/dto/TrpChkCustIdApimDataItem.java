package com.bct.ngtpa.apiservice.adapter.out.apim.registration.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Adapter-only TRPChkCustId response.data[] item.
 *
 * APIM returns isExist=true when the submitted customer id already exists.
 * Keep that meaning unchanged at the DTO boundary; availability is derived by
 * the result mapper, not by mutating the raw APIM value.
 */
public record TrpChkCustIdApimDataItem(
        @JsonProperty("cust-id") String customerId,
        @JsonProperty("isExist") Boolean isExist) {

    public boolean exists() {
        return Boolean.TRUE.equals(isExist);
    }

    @Override
    public String toString() {
        return "TrpChkCustIdApimDataItem[customerId=<redacted>, isExist=" + isExist + "]";
    }
}
