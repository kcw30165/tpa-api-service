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
public class GetContributionSummaryDispSrcItem {

    @JsonProperty("disp-src")
    private String dispSrc;

    @JsonProperty("src-desc")
    private String srcDesc;

    @JsonProperty("src-chin-desc")
    private String srcChinDesc;

    @JsonProperty("seq")
    private Integer seq;
}