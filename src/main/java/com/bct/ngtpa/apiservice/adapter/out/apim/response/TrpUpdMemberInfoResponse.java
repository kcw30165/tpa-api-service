package com.bct.ngtpa.apiservice.adapter.out.apim.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record TrpUpdMemberInfoResponse(
        @JsonProperty("response") Response response) {

    public record Response(
            @JsonProperty("err-message") String errMessage,
            @JsonProperty("data") List<TrpUpdMemberInfoData> data) {
    }
}
