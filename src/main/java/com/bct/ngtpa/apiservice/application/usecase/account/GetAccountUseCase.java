package com.bct.ngtpa.apiservice.application.usecase.account;

import com.bct.ngtpa.apiservice.application.dto.AccountContext;
import reactor.core.publisher.Mono;

/** Resolves display metadata for one specific account within the current authenticated session. */
public interface GetAccountUseCase {
  Mono<AccountContext> getAccount(GetAccountCommand command);
}
