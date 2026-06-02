package com.bct.ngtpa.apiservice.adapter.out.apim.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class GetMemberInfoApimRequest {

    @JsonProperty("policy-no")
    private String policyNo;

    @JsonProperty("cert-no")
    private String certNo;

    @JsonProperty("user-id")
    private String userId;

    @JsonProperty("env")
    private String accountEnv;
}
