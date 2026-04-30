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
public class UpdateNotificationReadStatusApimEnvelope {

    @JsonProperty("err-message")
    private String errMessage;

    @JsonProperty("response")
    private APIMResponsePayload<UpdateNotificationReadStatusApimDataItem> response;
}