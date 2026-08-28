package com.bct.ngtpa.apiservice.application.port.out;

import com.bct.ngtpa.apiservice.application.dto.FetchAccountBalanceCommand;
import com.bct.ngtpa.apiservice.application.dto.accountbalance.raw.RawAccountBalanceData;
import reactor.core.publisher.Mono;

@FunctionalInterface
public interface AccountBalanceSourcePort {
  Mono<RawAccountBalanceData> fetchRawAccountBalance(FetchAccountBalanceCommand command);
}
