package com.bct.ngtpa.apiservice.adapter.in.web.fundcomparison.response;

import java.util.List;

/**
 * Public success payload for {@code POST /api/v1/fund-comparison/prices}, per
 * docs/design/13-fund-comparison/02-api-contract.md section 2.3. Wrapped by the repository's
 * standard {@code MutationResponse} "result.data" convention by the controller (sub-task 3.4),
 * mirroring the options response's own wrapping precedent.
 */
public record FundComparisonPricesResponse(
    String fromDate, String toDate, List<FundComparisonSeriesResponse> series) {

  public FundComparisonPricesResponse {
    series = series == null ? List.of() : List.copyOf(series);
  }
}
