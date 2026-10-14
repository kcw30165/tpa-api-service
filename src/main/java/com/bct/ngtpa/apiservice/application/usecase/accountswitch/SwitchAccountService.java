package com.bct.ngtpa.apiservice.application.usecase.accountswitch;

import com.bct.ngtpa.apiservice.application.dto.AccountContext;
import com.bct.ngtpa.apiservice.application.port.out.AccountSwitchLookupPort;
import java.util.Objects;
import reactor.core.publisher.Mono;

/**
 * Default implementation of {@link SwitchAccountUseCase}.
 *
 * <p>Stateless: delegates to {@link AccountSwitchLookupPort#resolveTargetAccount(String, String)}
 * -- a dedicated port for this capability, separate from the web filter's external account/session
 * resolution port. No session mutation occurs. A resolution failure (target account not present in
 * the session's account list, or the session itself invalid) propagates unchanged as
 * {@code AuthenticatedPortalContextResolutionException}, which the existing authenticated-context
 * web exception handler already maps to 401 UNAUTHORIZED globally.
 */
public final class SwitchAccountService implements SwitchAccountUseCase {

  private final AccountSwitchLookupPort accountSwitchLookupPort;

  public SwitchAccountService(AccountSwitchLookupPort accountSwitchLookupPort) {
    this.accountSwitchLookupPort = Objects.requireNonNull(accountSwitchLookupPort);
  }

  @Override
  public Mono<AccountContext> switchAccount(SwitchAccountCommand command) {
    return accountSwitchLookupPort.resolveTargetAccount(
        command.targetAccountRef(), command.sessionId().value());
  }
}
