package com.bct.ngtpa.apiservice.adapter.out.apim.fundfactsheet.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * One fact sheet row of TRPGetFundFactSheet. {@code seq} is an {@link Integer} so that a missing
 * value is detectable.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TrpGetFundFactSheetApimRow(
    @JsonProperty("seq") Integer seq,
    @JsonProperty("name-eng") String nameEng,
    @JsonProperty("name-chi") String nameChi,
    @JsonProperty("link-eng") String linkEng,
    @JsonProperty("link-chi") String linkChi) {}
