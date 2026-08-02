package com.bct.ngtpa.apiservice.adapter.out.apim.forgotpassword.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record TrpResetPasswordApimDataItem(
        @JsonProperty("success") Boolean success,
        @JsonProperty("cust-id") String customerId,
        @JsonProperty("update-date") String updateDate,
        @JsonProperty("update-time") String updateTime) {
    @Override
    public String toString() {
        return "TrpResetPasswordApimDataItem[success=" + success
                + ", customerId=<redacted>, updateDate=<redacted>, updateTime=<redacted>]";
    }
}