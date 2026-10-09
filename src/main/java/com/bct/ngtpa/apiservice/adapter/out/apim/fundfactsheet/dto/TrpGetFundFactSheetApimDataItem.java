package com.bct.ngtpa.apiservice.adapter.out.apim.fundfactsheet.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/** One element of {@code response.data} of TRPGetFundFactSheet. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TrpGetFundFactSheetApimDataItem(
    @JsonProperty("FundFactSheet") List<TrpGetFundFactSheetApimRow> fundFactSheet) {}
