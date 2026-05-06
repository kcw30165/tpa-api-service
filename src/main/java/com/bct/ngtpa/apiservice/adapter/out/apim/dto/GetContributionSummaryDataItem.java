package com.bct.ngtpa.apiservice.adapter.out.apim.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class GetContributionSummaryDataItem {

    @JsonProperty("disp-mode")
    private String dispMode;

    @JsonProperty("currency")
    private String currency;

    @JsonProperty("dispSrc")
    private List<GetContributionSummaryDispSrcItem> dispSrc;

    @JsonProperty("contDtl")
    private List<GetContributionSummaryContDtlItem> contDtl;
}