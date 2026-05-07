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
public class UpdateNotificationReadStatusApimRequest {

    @JsonProperty("cert-no")
    private String certNo;

    @JsonProperty("env")
    private String env;

    @JsonProperty("mbr-type")
    private String mbrType;

    @JsonProperty("msg-code-long")
    private String msgCodeLong;

    @JsonProperty("policy-no")
    private String policyNo;

    @JsonProperty("ref-date")
    private String refDate;

    @JsonProperty("status")
    private String status;

    @JsonProperty("user-id")
    private String userId;
}