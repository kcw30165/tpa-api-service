package com.bct.ngtpa.apiservice.adapter.out.apim.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record TrpUpdMemberInfoData(
        @JsonProperty("success") Boolean success,
        @JsonProperty("ref-no") String refNo,
        @JsonProperty("submit-date") String submitDate,
        @JsonProperty("submit-time") String submitTime,
        @JsonProperty("errors") List<TrpUpdMemberInfoError> errors) {
}
