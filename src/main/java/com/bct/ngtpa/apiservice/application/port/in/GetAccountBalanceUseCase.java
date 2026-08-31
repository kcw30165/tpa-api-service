package com.bct.ngtpa.apiservice.application.port.in;

import com.bct.ngtpa.apiservice.application.dto.AccountBalanceResult;
import com.bct.ngtpa.apiservice.application.dto.GetAccountBalanceCommand;
import reactor.core.publisher.Mono;

@FunctionalInterface
public interface GetAccountBalanceUseCase {
  Mono<AccountBalanceResult> get(GetAccountBalanceCommand command);
}
