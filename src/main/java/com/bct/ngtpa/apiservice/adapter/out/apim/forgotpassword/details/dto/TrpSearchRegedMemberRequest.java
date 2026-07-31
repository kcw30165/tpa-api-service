package com.bct.ngtpa.apiservice.adapter.out.apim.forgotpassword.details.dto;
import com.fasterxml.jackson.annotation.JsonProperty;
public record TrpSearchRegedMemberRequest(@JsonProperty("search-by") String searchBy,
        @JsonProperty("search-value") String searchValue, @JsonProperty("dob") String dob,
        @JsonProperty("email") String email, @JsonProperty("mobile-number") String mobileNumber,
        @JsonProperty("cust-id") String customerId) {
    @Override public String toString() { return "TrpSearchRegedMemberRequest[searchBy=" + searchBy + ", protected=<redacted>]"; }
}
