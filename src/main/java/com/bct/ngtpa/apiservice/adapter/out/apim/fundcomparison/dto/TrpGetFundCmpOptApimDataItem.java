package com.bct.ngtpa.apiservice.adapter.out.apim.fundcomparison.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * Downstream wire representation of one TRPGetFundCmpOpt {@code data[]} entry, per
 * docs/design/13-fund-comparison/03-downstream-integration.md section 3.1: {@code time-range}
 * (comma-separated string, returned RAW/unparsed -- normalization is {@code
 * FundComparisonTimeRangeParser}'s responsibility, not this DTO's) and {@code fund-list} (array of
 * fund entries).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TrpGetFundCmpOptApimDataItem(
    @JsonProperty("time-range") String timeRange,
    @JsonProperty("fund-list") List<TrpGetFundCmpOptApimFundItem> fundList) {}
