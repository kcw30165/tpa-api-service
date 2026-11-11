package com.bct.ngtpa.apiservice.application.port.out;

import com.bct.ngtpa.apiservice.application.dto.investmentchoice.InvestmentChoiceCancelCommand;
import com.bct.ngtpa.apiservice.application.dto.investmentchoice.InvestmentChoiceCancellation;
import reactor.core.publisher.Mono;

public interface InvestmentChoiceCancelPort {

  Mono<InvestmentChoiceCancellation> cancel(InvestmentChoiceCancelCommand command);
}
