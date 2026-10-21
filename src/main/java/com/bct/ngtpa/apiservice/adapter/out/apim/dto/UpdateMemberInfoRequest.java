package com.bct.ngtpa.apiservice.adapter.out.apim.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;

public record UpdateMemberInfoRequest(
    @JsonProperty("policy-no") String policyNo,
    @JsonProperty("cert-no") String certNo,
    @JsonProperty("update-fields") Map<String, Object> updateFields,
    @JsonProperty("login-user-id") String loginUserId,
    @JsonProperty("login-user-role") String loginUserRole) {}
