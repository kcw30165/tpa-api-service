package com.bct.ngtpa.apiservice.adapter.in.web.fundcomparison.response;

import java.util.Objects;

/**
 * Wraps {@link FundComparisonPricesResponse} under a "data" field, mirroring {@code
 * FundComparisonOptionsResultResponse}'s exact confirmed one-field convention (script 066
 * evidence), so that {@code MutationResponse}'s serialized "result" field carries a nested "data"
 * key.
 */
public record FundComparisonPricesResultResponse(FundComparisonPricesResponse data) {

  public FundComparisonPricesResultResponse {
    Objects.requireNonNull(data, "data must not be null");
  }
}
