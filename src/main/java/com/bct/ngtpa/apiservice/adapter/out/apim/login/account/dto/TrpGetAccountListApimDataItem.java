package com.bct.ngtpa.apiservice.adapter.out.apim.login.account.dto;

import com.fasterxml.jackson.annotation.*;

@JsonIgnoreProperties(ignoreUnknown = true)
public record TrpGetAccountListApimDataItem(
        @JsonProperty("env") String accountEnv,
        @JsonProperty("trust-code") String trustCode,
        @JsonProperty("policy-no") String policyNo,
        @JsonProperty("sch-type") String schemeType,
        @JsonProperty("cert-no") Integer certNo,
        @JsonProperty("term-status") String termStatus,
        @JsonProperty("term-complete-date") String termCompleteDate) {
    @Override
    public String toString() {
        return "TrpGetAccountListApimDataItem[accountEnv=<redacted>, trustCode=<redacted>, policyNo=<redacted>, schemeType="
                + schemeType + ", certNo=<redacted>, termStatus=" + (termStatus == null ? "blank" : "present")
                + ", termCompleteDate=<redacted>]";
    }
}
