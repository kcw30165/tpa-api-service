package com.bct.ngtpa.apiservice.adapter.out.apim.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateMemberInfoApimRequest {

    @JsonProperty("policy-no")
    private String policyNo;

    @JsonProperty("cert-no")
    private String certNo;

    @JsonProperty("env")
    private String accountEnv;

    @JsonProperty("user-id")
    private String userId;

    @JsonProperty("user-role")
    private String userRole;

    @JsonProperty("apply-all")
    private Boolean applyToAllAccounts;

    @JsonProperty("update-fields")
    private Map<String, Object> updateFields;
}
