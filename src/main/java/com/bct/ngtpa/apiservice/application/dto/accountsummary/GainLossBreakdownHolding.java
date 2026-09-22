package com.bct.ngtpa.apiservice.application.dto.accountsummary;

/**
 * One fund row. balance and portfolioPercentage are rendered strings summed across every accBal
 * row for this fund, keyed by trimmed fund code (R-5).
 *
 * <p>portfolioPercentage is null when every contributing row's percentage is unreported (the
 * pre-deployment transition state, R-6) - deliberately distinct from {@code "0.0"}, which means
 * the fund contributes zero because it has no holdings at all (a sold fund, R-4). A fund with
 * some rows reported and some unreported is a technical contract violation, not represented here
 * because the assembler rejects it before construction (R-5).
 */
public record GainLossBreakdownHolding(
    String fundCode,
    String fundName,
    Integer fundSequence,
    String balance,
    String portfolioPercentage,
    String gainLoss) {}
