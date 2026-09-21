package com.bct.ngtpa.apiservice.adapter.in.web.response.accountsummary;

import java.util.List;

/**
 * Gain/loss breakdown section, present only on {@code /api/v1/account/summary}.
 *
 * <p>{@code totalGainLoss} is the exact sum of every {@code TRPGetInvestGL} fund, so this endpoint
 * and {@code /api/v1/account/gain-loss} report an identical total for the same account, period,
 * currency, and reference date.
 */
public record GainLossBreakdownResponse(
    String period,
    String gainLossAsOfDate,
    String totalValue,
    String totalGainLoss,
    List<GainLossBreakdownHoldingResponse> holdings) {

  public GainLossBreakdownResponse {
    holdings = holdings == null ? List.of() : List.copyOf(holdings);
  }
}
