package com.bct.ngtpa.apiservice.adapter.out.apim.forgotpassword.details.dto;
import com.fasterxml.jackson.annotation.JsonProperty;
public record TrpSearchRegedMemberDataItem(@JsonProperty("email") String email,
        @JsonProperty("mobile-number") String mobileNumber, @JsonProperty("env") String env,
        @JsonProperty("trust-code") String trustCode) {
    @Override public String toString() { return "TrpSearchRegedMemberDataItem[contact=<redacted>]"; }
}
