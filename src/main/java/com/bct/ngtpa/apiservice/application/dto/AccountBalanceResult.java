package com.bct.ngtpa.apiservice.application.dto;

import java.util.List;

public record AccountBalanceResult(
    String asOfDate,
    CurrencySelection currencySelection,
    AccountOverview accountOverview,
    List<InvestmentPortfolioItem> investmentPortfolio) {
  public AccountBalanceResult {
    investmentPortfolio =
        investmentPortfolio == null ? List.of() : List.copyOf(investmentPortfolio);
  }
}
