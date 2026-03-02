package com.bct.ngtpa.apiservice.dto.apim;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class DataItem {

    @JsonProperty("price-date")
    private String priceDate;

    @JsonProperty("DispSrc")
    private List<DispSrcItem> dispSrc;

    @JsonProperty("FundDtl")
    private List<FundDtlItem> fundDtl;

    @JsonProperty("AccBal")
    private List<AccBalItem> accBal;

    @JsonProperty("AccBalMisc")
    private List<AccBalMiscItem> accBalMisc;
}
