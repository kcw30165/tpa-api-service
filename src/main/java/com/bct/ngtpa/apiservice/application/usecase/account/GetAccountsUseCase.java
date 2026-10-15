package com.bct.ngtpa.apiservice.application.usecase.account;

import com.bct.ngtpa.apiservice.application.dto.AccountContext;
import java.util.List;
import reactor.core.publisher.Mono;

/** Resolves display metadata for every account within the current authenticated session. */
public interface GetAccountsUseCase {
  Mono<List<AccountContext>> getAccounts(GetAccountsCommand command);
}
