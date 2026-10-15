package com.bct.ngtpa.apiservice.application.usecase.account;

import com.bct.ngtpa.apiservice.application.dto.AccountContext;
import com.bct.ngtpa.apiservice.application.port.out.AccountLookupPort;
import java.util.Objects;
import reactor.core.publisher.Mono;

/**
 * Default implementation of {@link GetAccountUseCase}.
 *
 * <p>Stateless: delegates to {@link AccountLookupPort#getAccount(String, String)}. A resolution
 * failure (target account not present in the session's account list, or the session itself invalid)
 * propagates unchanged.
 */
public final class GetAccountService implements GetAccountUseCase {

  private final AccountLookupPort accountLookupPort;

  public GetAccountService(AccountLookupPort accountLookupPort) {
    this.accountLookupPort = Objects.requireNonNull(accountLookupPort);
  }

  @Override
  public Mono<AccountContext> getAccount(GetAccountCommand command) {
    return accountLookupPort.getAccount(command.targetAccountRef(), command.sessionId().value());
  }
}
