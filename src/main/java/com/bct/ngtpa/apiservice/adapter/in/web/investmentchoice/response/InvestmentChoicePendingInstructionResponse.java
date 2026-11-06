package com.bct.ngtpa.apiservice.adapter.in.web.investmentchoice.response;

import java.util.List;

public record InvestmentChoicePendingInstructionResponse(
    String referenceNo,
    String submitDate,
    String submitTime,
    List<InvestmentChoicePendingAllocationResponse> allocations) {

  public InvestmentChoicePendingInstructionResponse {
    allocations = List.copyOf(allocations);
  }
}
