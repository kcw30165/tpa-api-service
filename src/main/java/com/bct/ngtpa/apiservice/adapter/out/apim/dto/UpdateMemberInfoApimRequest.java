package com.bct.ngtpa.apiservice.adapter.out.apim.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class UpdateMemberInfoApimRequest {

    @JsonProperty("policy-no")
    private String policyNo;

    @JsonProperty("cert-no")
    private String certNo;

    @JsonProperty("env")
    private String accountEnv;

    @JsonProperty("user-id")
    private String userId;

    @JsonProperty("update-fields")
    private Map<String, Object> updateFields;
}
