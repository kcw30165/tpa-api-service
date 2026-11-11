package com.bct.ngtpa.apiservice.adapter.in.web.investmentchoice.response;

import java.util.List;

public record InvestmentChoiceHistoryResponse(
    String effectiveDate, List<InvestmentChoiceAllocationResponse> allocations) {

  public InvestmentChoiceHistoryResponse {
    allocations = List.copyOf(allocations);
  }
}
