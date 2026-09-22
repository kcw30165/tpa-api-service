package com.bct.ngtpa.apiservice.adapter.in.web.response.accountsummary;

/**
 * One fund row of the Portfolio Overview holdings table.
 *
 * <p>All monetary values are pre-rendered strings. The BFF performs no rounding: balance and
 * percentage are summed across the fund's {@code accBal} rows and rendered once at their configured
 * precision, and gain/loss comes straight from {@code TRPGetInvestGL}.
 *
 * <p>Sign is carried by the numeric string itself. Currency symbols, {@code +}/{@code -} prefixes,
 * colours, and chart geometry are front-end concerns.
 */
public record GainLossBreakdownHoldingResponse(
    String fundCode,
    String fundName,
    Integer fundSequence,
    String balance,
    String portfolioPercentage,
    String gainLoss) {}
