package com.bct.ngtpa.apiservice.adapter.in.web.response.accountbalance;

import com.fasterxml.jackson.annotation.JsonInclude;

public record InvestmentPortfolioItemResponse(
    String subDisplayAccountLabel,
    String fundName,
    @JsonInclude(JsonInclude.Include.NON_NULL) String fxRate,
    @JsonInclude(JsonInclude.Include.NON_NULL) String factsheetUrl) {}
