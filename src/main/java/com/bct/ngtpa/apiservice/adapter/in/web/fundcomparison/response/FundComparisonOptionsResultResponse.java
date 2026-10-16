package com.bct.ngtpa.apiservice.adapter.in.web.fundcomparison.response;

import java.util.Objects;

/**
 * Wraps {@link FundComparisonOptionsResponse} under a "data" field, mirroring the established
 * {@code FundPriceModeResultResponse} convention so that {@code MutationResponse}'s serialized
 * "result" field carries a nested "data" key.
 */
public record FundComparisonOptionsResultResponse(FundComparisonOptionsResponse data) {

  public FundComparisonOptionsResultResponse {
    Objects.requireNonNull(data, "data must not be null");
  }
}
