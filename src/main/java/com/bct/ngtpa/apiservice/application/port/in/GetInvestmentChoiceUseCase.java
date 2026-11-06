package com.bct.ngtpa.apiservice.application.port.in;

import com.bct.ngtpa.apiservice.application.dto.investmentchoice.GetInvestmentChoiceCommand;
import com.bct.ngtpa.apiservice.application.dto.investmentchoice.InvestmentChoiceResult;
import reactor.core.publisher.Mono;

public interface GetInvestmentChoiceUseCase {

  Mono<InvestmentChoiceResult> get(GetInvestmentChoiceCommand command);
}
