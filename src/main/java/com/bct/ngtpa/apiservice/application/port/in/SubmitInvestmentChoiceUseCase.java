package com.bct.ngtpa.apiservice.application.port.in;

import com.bct.ngtpa.apiservice.application.dto.investmentchoice.InvestmentChoiceSubmitOutcome;
import com.bct.ngtpa.apiservice.application.dto.investmentchoice.SubmitInvestmentChoiceCommand;
import reactor.core.publisher.Mono;

public interface SubmitInvestmentChoiceUseCase {

  Mono<InvestmentChoiceSubmitOutcome> submit(SubmitInvestmentChoiceCommand command);
}
