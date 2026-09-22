package com.bct.ngtpa.apiservice.application.port.in;

import com.bct.ngtpa.apiservice.application.dto.accountsummary.AccountSummaryResult;
import com.bct.ngtpa.apiservice.application.dto.accountsummary.GetAccountSummaryCommand;
import reactor.core.publisher.Mono;

@FunctionalInterface
public interface GetAccountSummaryUseCase {
  Mono<AccountSummaryResult> get(GetAccountSummaryCommand command);
}
