package com.bct.ngtpa.apiservice.dto.apim;

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
public class AccountBalanceRequest {

    @JsonProperty("cert-no")
    private Integer certNo;

    private String channel;

    private Integer decplace;

    private String env;

    @JsonProperty("pcnt-cal-mtd")
    private String pcntCalMtd;

    @JsonProperty("policy-no")
    private String policyNo;

    @JsonProperty("ref-date")
    private String refDate;

    @JsonProperty("trunc-round")
    private Boolean truncRound;
}
