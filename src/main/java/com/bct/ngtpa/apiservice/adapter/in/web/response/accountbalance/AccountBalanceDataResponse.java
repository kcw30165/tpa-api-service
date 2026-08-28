package com.bct.ngtpa.apiservice.adapter.in.web.response.accountbalance;

import java.util.List;

public record AccountBalanceDataResponse(
    String asOfDate,
    CurrencySelectionResponse currencySelection,
    AccountOverviewResponse accountOverview,
    List<InvestmentPortfolioItemResponse> investmentPortfolio) {
  public AccountBalanceDataResponse {
    investmentPortfolio = investmentPortfolio == null ? List.of() : List.copyOf(investmentPortfolio);
  }
}
