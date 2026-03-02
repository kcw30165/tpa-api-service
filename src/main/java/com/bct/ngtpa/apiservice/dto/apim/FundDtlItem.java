package com.bct.ngtpa.apiservice.dto.apim;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class FundDtlItem {

    @JsonProperty("fund-code")
    private String fundCode;

    @JsonProperty("fund-name")
    private String fundName;

    @JsonProperty("chin-fund-name")
    private String chinFundName;

    @JsonProperty("fund-seq")
    private Integer fundSeq;

    @JsonProperty("fund-color")
    private String fundColor;

    @JsonProperty("fund-price")
    private BigDecimal fundPrice;

    @JsonProperty("is-new")
    private Boolean isNew;

    @JsonProperty("disp-group")
    private String dispGroup;

    @JsonProperty("detail-url")
    private String detailUrl;

    @JsonProperty("fund-curr")
    private String fundCurr;

    @JsonProperty("exchange-rate")
    private BigDecimal exchangeRate;

    @JsonProperty("fund-nature")
    private String fundNature;
}
