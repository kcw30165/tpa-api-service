package com.bct.ngtpa.apiservice.adapter.out.apim.login.account.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record TrpGetAccountListApimRequest(
        @JsonProperty("search-key") String searchKey, 
        @JsonProperty("search-value") String searchValue,
        @JsonProperty("login-user-id") String loginUserId, 
        @JsonProperty("login-user-role") String loginUserRole) {
    @Override
    public String toString() {
        return "TrpGetAccountListApimRequest[searchKey=" + searchKey
                + ", searchValue=<redacted>, loginUserId=<redacted>, loginUserRole=" + loginUserRole + "]";
    }
}
