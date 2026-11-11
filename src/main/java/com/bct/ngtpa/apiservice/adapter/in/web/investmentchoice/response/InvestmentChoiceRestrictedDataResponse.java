package com.bct.ngtpa.apiservice.adapter.in.web.investmentchoice.response;

import com.bct.ngtpa.apiservice.application.dto.investmentchoice.InvestmentChoiceScenario;
import java.util.List;
import java.util.Objects;

public record InvestmentChoiceRestrictedDataResponse(
    String accountNumber,
    InvestmentChoiceScenario scenario,
    List<InvestmentChoiceHistoryResponse> history)
    implements InvestmentChoiceDataResponse {

  public InvestmentChoiceRestrictedDataResponse {
    Objects.requireNonNull(scenario, "scenario must not be null");
    if (scenario == InvestmentChoiceScenario.NORMAL) {
      throw new IllegalArgumentException("Restricted response requires a restricted scenario");
    }
    history = List.copyOf(history);
  }
}
