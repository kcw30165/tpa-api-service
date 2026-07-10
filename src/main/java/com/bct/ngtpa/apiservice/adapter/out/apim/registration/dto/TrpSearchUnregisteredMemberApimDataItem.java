package com.bct.ngtpa.apiservice.adapter.out.apim.registration.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record TrpSearchUnregisteredMemberApimDataItem(
        @JsonProperty("email") String email,
        @JsonProperty("mobile-number") String mobileNumber,
        @JsonProperty("env") String env,
        @JsonProperty("trust-code") String trustCode) {
    @Override
    public String toString() {
        return "TrpSearchUnregisteredMemberApimDataItem[email=<redacted>, mobileNumber=<redacted>, env=<redacted>, trustCode=<redacted>]";
    }
}
