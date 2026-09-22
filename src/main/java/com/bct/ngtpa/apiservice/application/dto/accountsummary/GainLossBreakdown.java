package com.bct.ngtpa.apiservice.application.dto.accountsummary;

import java.util.List;

/**
 * totalValue and totalGainLoss are reused directly from the existing Account Balance and Account
 * Gain/Loss results rather than recomputed here, so summary can never silently disagree with either
 * standalone endpoint's total for the same account, period, currency, and reference date.
 */
public record GainLossBreakdown(
    String period,
    String gainLossAsOfDate,
    String totalValue,
    String totalGainLoss,
    List<GainLossBreakdownHolding> holdings) {

  public GainLossBreakdown {
    holdings = holdings == null ? List.of() : List.copyOf(holdings);
  }
}
