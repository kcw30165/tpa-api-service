package com.bct.ngtpa.apiservice.adapter.out.apim.registration.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record TrpSearchUnregMemberApimDataItem(
        @JsonProperty("email") String email,
        @JsonProperty("mobile-number") String mobileNumber,
        @JsonProperty("env") String env,
        @JsonProperty("trust-code") String trustCode) {
    @Override
    public String toString() {
        return "TrpSearchUnregMemberApimDataItem[email=<redacted>, mobileNumber=<redacted>, env=<redacted>, trustCode=<redacted>]";
    }
}
