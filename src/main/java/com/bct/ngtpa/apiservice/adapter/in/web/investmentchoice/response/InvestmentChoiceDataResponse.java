package com.bct.ngtpa.apiservice.adapter.in.web.investmentchoice.response;

import com.bct.ngtpa.apiservice.application.dto.investmentchoice.InvestmentChoiceScenario;
import java.util.List;

public sealed interface InvestmentChoiceDataResponse
    permits InvestmentChoiceNormalDataResponse, InvestmentChoiceRestrictedDataResponse {

  String accountNumber();

  InvestmentChoiceScenario scenario();

  List<InvestmentChoiceHistoryResponse> history();
}
