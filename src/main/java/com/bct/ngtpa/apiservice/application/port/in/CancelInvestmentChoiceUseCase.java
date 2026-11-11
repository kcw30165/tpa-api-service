package com.bct.ngtpa.apiservice.application.port.in;

import com.bct.ngtpa.apiservice.application.dto.investmentchoice.CancelInvestmentChoiceCommand;
import com.bct.ngtpa.apiservice.application.dto.investmentchoice.InvestmentChoiceCancelOutcome;
import reactor.core.publisher.Mono;

public interface CancelInvestmentChoiceUseCase {

  Mono<InvestmentChoiceCancelOutcome> cancel(CancelInvestmentChoiceCommand command);
}
