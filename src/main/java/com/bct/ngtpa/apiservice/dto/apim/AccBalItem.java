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
public class AccBalItem {

    @JsonProperty("disp-src")
    private String dispSrc;

    @JsonProperty("sub-disp-src")
    private String subDispSrc;

    @JsonProperty("fund-code")
    private String fundCode;

    private BigDecimal unit;

    private BigDecimal nav;
}
