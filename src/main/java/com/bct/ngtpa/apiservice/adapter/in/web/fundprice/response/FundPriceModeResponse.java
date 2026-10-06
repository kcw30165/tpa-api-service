package com.bct.ngtpa.apiservice.adapter.in.web.fundprice.response;

import java.util.List;

/**
 * Response payload for {@code GET /api/v1/fund-price/mode}, per
 * fund-price-api-contract.md Section 1. Wrapped by {@link FundPriceModeResultResponse}
 * under the repository-standard {@code MutationResponse}'s "result" field, mirroring the
 * "result.data" nesting convention already used by analogous authenticated read endpoints
 * (confirmed, script 173's investigation of AccountSummaryController/AccountGainLossController).
 */
public record FundPriceModeResponse(
    String mode,
    List<String> availableFundPriceDates,
    int maximumFundPriceHistoryMonths,
    String referenceDate) {

  public FundPriceModeResponse {
    availableFundPriceDates =
        availableFundPriceDates == null ? List.of() : List.copyOf(availableFundPriceDates);
  }
}
