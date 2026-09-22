package com.bct.ngtpa.apiservice.adapter.in.web.response.accountsummary;

import java.util.List;

public record InvestmentPortfolioResponse(
    List<FundLevelDisplayAccountResponse> fundLevelView,
    List<SubDisplayLevelDisplayAccountResponse> subDisplayAccountLevelView) {
  public InvestmentPortfolioResponse {
    fundLevelView = fundLevelView == null ? List.of() : List.copyOf(fundLevelView);
    subDisplayAccountLevelView =
        subDisplayAccountLevelView == null ? List.of() : List.copyOf(subDisplayAccountLevelView);
  }
}
