package com.bct.ngtpa.apiservice.application.port.out;

import com.bct.ngtpa.apiservice.application.dto.investmentchoice.InvestmentChoiceDetails;
import com.bct.ngtpa.apiservice.application.dto.investmentchoice.InvestmentChoiceDetailsCommand;
import reactor.core.publisher.Mono;

public interface InvestmentChoiceDetailsPort {
  Mono<InvestmentChoiceDetails> load(InvestmentChoiceDetailsCommand command);
}
