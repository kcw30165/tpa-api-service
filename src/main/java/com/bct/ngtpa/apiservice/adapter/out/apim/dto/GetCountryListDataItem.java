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
public class GetCountryListDataItem {

    @JsonProperty("country-code")
    private String countryCode;

    @JsonProperty("country-name-eng")
    private String countryNameEng;

    @JsonProperty("country-name-chi")
    private String countryNameChi;

    @JsonProperty("calling-code")
    private String callingCode;

    @JsonProperty("alpha-two-code")
    private String alphaTwoCode;
}
