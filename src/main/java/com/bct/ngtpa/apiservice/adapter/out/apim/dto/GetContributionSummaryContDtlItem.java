package com.bct.ngtpa.apiservice.adapter.out.apim.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class GetContributionSummaryContDtlItem {

    @JsonProperty("disp-src")
    private String dispSrc;

    @JsonProperty("cover-from")
    private String coverFrom;

    @JsonProperty("cover-to")
    private String coverTo;

    @JsonProperty("deal-date")
    private String dealDate;

    @JsonProperty("cont-amt")
    private BigDecimal contAmt;
}