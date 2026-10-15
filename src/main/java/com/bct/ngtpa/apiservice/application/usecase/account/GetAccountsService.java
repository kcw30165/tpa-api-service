package com.bct.ngtpa.apiservice.application.usecase.account;

import com.bct.ngtpa.apiservice.application.dto.AccountContext;
import com.bct.ngtpa.apiservice.application.port.out.AccountLookupPort;
import java.util.List;
import java.util.Objects;
import reactor.core.publisher.Mono;

/**
 * Default implementation of {@link GetAccountsUseCase}.
 *
 * <p>Stateless: delegates to {@link AccountLookupPort#listAccounts(String)}. A session-validity
 * failure propagates unchanged.
 */
public final class GetAccountsService implements GetAccountsUseCase {

  private final AccountLookupPort accountLookupPort;

  public GetAccountsService(AccountLookupPort accountLookupPort) {
    this.accountLookupPort = Objects.requireNonNull(accountLookupPort);
  }

  @Override
  public Mono<List<AccountContext>> getAccounts(GetAccountsCommand command) {
    return accountLookupPort.listAccounts(command.sessionId().value());
  }
}
