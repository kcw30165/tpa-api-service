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
public class AccBalMiscItem {

    @JsonProperty("fund-code")
    private String fundCode;

    @JsonProperty("vested-bal")
    private BigDecimal vestedBal;

    @JsonProperty("total-with-interest")
    private BigDecimal totalWithInterest;

    private BigDecimal pcnt;

    @JsonProperty("transit-amt")
    private BigDecimal transitAmt;
}
