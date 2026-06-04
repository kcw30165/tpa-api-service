package com.bct.ngtpa.apiservice.adapter.out.apim.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;

public record UpdateMemberInfoRequest(
        @JsonProperty("policy-no") String policyNo,
        @JsonProperty("cert-no") String certNo,
        @JsonProperty("env") String env,
        @JsonProperty("user-id") String userId,
        @JsonProperty("update-fields") Map<String, Object> updateFields) {
}
