package com.bct.ngtpa.apiservice.adapter.in.web.fundcomparison.response;

import java.math.BigDecimal;

/**
 * One public date/unit-price observation within a {@link FundComparisonSeriesResponse}, per
 * docs/design/13-fund-comparison/02-api-contract.md section 2.3. {@code unitPrice} is always the
 * downstream bid-price; offer-price is never exposed (confirmed non-goal,
 * 06-decisions-and-non-goals.md).
 */
public record FundComparisonPricePointResponse(String priceDate, BigDecimal unitPrice) {}
