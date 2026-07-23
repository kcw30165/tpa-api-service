package com.bct.ngtpa.apiservice.adapter.out.apim.login.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record TrpMemberLoginApimRequest(
        @JsonProperty("cust-id") String custId,
        @JsonProperty("password") String password) {

    @Override
    public String toString() {
        return "TrpMemberLoginApimRequest[custId=<redacted>, password=<redacted>]";
    }
}
