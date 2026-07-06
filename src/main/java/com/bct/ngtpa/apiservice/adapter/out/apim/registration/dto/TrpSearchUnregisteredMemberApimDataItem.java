package com.bct.ngtpa.apiservice.adapter.out.apim.registration.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record TrpSearchUnregisteredMemberApimDataItem(
        @JsonProperty("email") String email,
        @JsonProperty("mobile-number") String mobileNumber,
        @JsonProperty("policy-no") String policyNo,
        @JsonProperty("cert-no") String certNo,
        @JsonProperty("env") String env,
        @JsonProperty("trust-code") String trustCode) {
    @Override
    public String toString() {
        return "TrpSearchUnregisteredMemberApimDataItem[email=<redacted>, mobileNumber=<redacted>, policyNo=<redacted>, certNo=<redacted>, env=<redacted>, trustCode=<redacted>]";
    }
}
