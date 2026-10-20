package com.bct.ngtpa.apiservice.adapter.in.web.fundcomparison.response;

import java.util.List;

/**
 * Public per-fund price series for {@code POST /api/v1/fund-comparison/prices}'s success response,
 * per docs/design/13-fund-comparison/02-api-contract.md section 2.3.
 */
public record FundComparisonSeriesResponse(
    String fundCode, List<FundComparisonPricePointResponse> prices) {

  public FundComparisonSeriesResponse {
    prices = prices == null ? List.of() : List.copyOf(prices);
  }
}
