package com.bct.ngtpa.apiservice.adapter.out.apim.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
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
public class UpdateMemberInfoApimDataItem {
    @JsonProperty("success")
    private boolean success;
    @JsonProperty("policy-no")
    private String policyNo;
    @JsonProperty("cert-no")
    private String certNo;
    @JsonProperty("env")
    private String env;
    @JsonProperty("ref-no")
    private String refNo;
    @JsonProperty("submit-date")
    private String submitDate;
    @JsonProperty("submit-time")
    private String submitTime;
    @JsonProperty("errors")
    private List<UpdateMemberInfoApimError> errors;
}
