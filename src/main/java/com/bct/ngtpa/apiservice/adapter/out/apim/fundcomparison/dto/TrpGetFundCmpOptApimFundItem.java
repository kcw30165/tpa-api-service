package com.bct.ngtpa.apiservice.adapter.out.apim.fundcomparison.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Downstream wire representation of a single fund entry inside TRPGetFundCmpOpt's {@code
 * fund-list}, per docs/design/13-fund-comparison/03-downstream-integration.md section 3.1. Raw
 * downstream field names confined here; no validation or normalization is performed on this record
 * itself (confirmed by the adapter, which validates structurally before constructing {@link
 * RawFundComparisonOptionsData}).
 */
public record TrpGetFundCmpOptApimFundItem(
    @JsonProperty("fund-code") String fundCode,
    @JsonProperty("fund-name") String fundName,
    @JsonProperty("chin-fund-name") String chinFundName) {}
