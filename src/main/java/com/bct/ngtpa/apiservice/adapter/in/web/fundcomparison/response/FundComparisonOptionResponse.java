package com.bct.ngtpa.apiservice.adapter.in.web.fundcomparison.response;

/**
 * Public response entry for a single fund within the options endpoint, per
 * docs/design/13-fund-comparison/02-api-contract.md section 2.2: only {@code fundCode}/{@code
 * fundName} are exposed. {@code fundName} is already the display-selected name for the request's
 * language -- there is no separate Chinese field in the public contract.
 */
public record FundComparisonOptionResponse(String fundCode, String fundName) {}
