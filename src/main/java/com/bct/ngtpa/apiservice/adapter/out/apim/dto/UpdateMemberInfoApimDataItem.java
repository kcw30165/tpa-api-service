package com.bct.ngtpa.apiservice.adapter.out.apim.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
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
    
    @JsonProperty("ref-no")
    private String refNo;

    @JsonProperty("submit-date")
    private String submitDate;

    @JsonProperty("submit-time")
    private String submitTime;

}
