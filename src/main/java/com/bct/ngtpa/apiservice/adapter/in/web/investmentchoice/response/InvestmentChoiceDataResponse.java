package com.bct.ngtpa.apiservice.adapter.in.web.investmentchoice.response;

import java.util.List;

public record InvestmentChoiceDataResponse(
    String accountNumber,
    int intervalPercentage,
    List<InvestmentChoiceSwitchSourceResponse> switchSources,
    List<InvestmentChoiceFundResponse> funds,
    List<InvestmentChoiceHistoryResponse> history,
    List<InvestmentChoicePendingInstructionResponse> pendingInstructions) {

  public InvestmentChoiceDataResponse {
    switchSources = List.copyOf(switchSources);
    funds = List.copyOf(funds);
    history = List.copyOf(history);
    pendingInstructions = List.copyOf(pendingInstructions);
  }
}
