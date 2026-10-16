package com.bct.ngtpa.apiservice.adapter.in.web.fundcomparison.response;

import java.util.List;

/**
 * Public response payload for {@code POST /api/v1/fund-comparison/options}, per
 * docs/design/13-fund-comparison/02-api-contract.md section 2.2. Wrapped by {@link
 * FundComparisonOptionsResultResponse} under the repository-standard {@code MutationResponse}'s
 * "result" field, mirroring the {@code FundPriceModeResponse}/{@code FundPriceModeResultResponse}
 * convention.
 */
public record FundComparisonOptionsResponse(
    List<String> timeRanges, String defaultTimeRange, List<FundComparisonOptionResponse> funds) {

  public FundComparisonOptionsResponse {
    timeRanges = timeRanges == null ? List.of() : List.copyOf(timeRanges);
    funds = funds == null ? List.of() : List.copyOf(funds);
  }
}
