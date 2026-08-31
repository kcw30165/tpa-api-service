package com.bct.ngtpa.apiservice.application.port.out;

import com.bct.ngtpa.apiservice.application.dto.AccountBalanceResult;
import com.bct.ngtpa.apiservice.application.dto.FetchAccountBalanceCommand;
import reactor.core.publisher.Mono;

@FunctionalInterface
public interface AccountBalanceDataPort {
  Mono<AccountBalanceResult> fetchAccountBalance(FetchAccountBalanceCommand command);
}
