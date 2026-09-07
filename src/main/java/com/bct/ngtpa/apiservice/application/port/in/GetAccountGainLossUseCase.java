package com.bct.ngtpa.apiservice.application.port.in;

import com.bct.ngtpa.apiservice.application.dto.accountgainloss.AccountGainLossResult;
import com.bct.ngtpa.apiservice.application.dto.accountgainloss.GetAccountGainLossCommand;
import reactor.core.publisher.Mono;

public interface GetAccountGainLossUseCase {
  Mono<AccountGainLossResult> get(GetAccountGainLossCommand command);
}
