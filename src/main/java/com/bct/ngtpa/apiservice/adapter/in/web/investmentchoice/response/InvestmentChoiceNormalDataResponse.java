package com.bct.ngtpa.apiservice.adapter.in.web.investmentchoice.response;

import com.bct.ngtpa.apiservice.application.dto.investmentchoice.InvestmentChoiceScenario;
import java.util.List;

public record InvestmentChoiceNormalDataResponse(
    String accountNumber,
    InvestmentChoiceScenario scenario,
    int intervalPercentage,
    List<InvestmentChoiceSwitchSourceResponse> switchSources,
    List<InvestmentChoiceFundResponse> funds,
    List<InvestmentChoiceHistoryResponse> history,
    List<InvestmentChoicePendingInstructionResponse> pendingInstructions)
    implements InvestmentChoiceDataResponse {

  public InvestmentChoiceNormalDataResponse {
    if (scenario != InvestmentChoiceScenario.NORMAL) {
      throw new IllegalArgumentException("NORMAL response requires NORMAL scenario");
    }
    switchSources = List.copyOf(switchSources);
    funds = List.copyOf(funds);
    history = List.copyOf(history);
    pendingInstructions = List.copyOf(pendingInstructions);
  }
}
