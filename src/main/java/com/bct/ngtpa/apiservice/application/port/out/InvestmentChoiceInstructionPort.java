package com.bct.ngtpa.apiservice.application.port.out;

import com.bct.ngtpa.apiservice.application.dto.investmentchoice.InvestmentChoiceInstructionCommand;
import com.bct.ngtpa.apiservice.application.dto.investmentchoice.InvestmentChoiceSubmission;
import reactor.core.publisher.Mono;

public interface InvestmentChoiceInstructionPort {

  Mono<InvestmentChoiceSubmission> submit(InvestmentChoiceInstructionCommand command);
}
