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
public class DispSrcItem {

    @JsonProperty("disp-src")
    private String dispSrc;

    @JsonProperty("src-desc")
    private String srcDesc;

    @JsonProperty("src-chin-desc")
    private String srcChinDesc;

    @JsonProperty("sub-disp-src")
    private String subDispSrc;

    @JsonProperty("sub-src-desc")
    private String subSrcDesc;

    @JsonProperty("sub-src-chin-desc")
    private String subSrcChinDesc;

    @JsonProperty("disp-group")
    private String dispGroup;

    private Integer seq;
}
