package com.bct.ngtpa.apiservice.adapter.out.apim.response;

import com.fasterxml.jackson.annotation.JsonProperty;

public record TrpUpdMemberInfoError(
        @JsonProperty("type") String type,
        @JsonProperty("fields") String fields,
        @JsonProperty("code") String code) {
}
