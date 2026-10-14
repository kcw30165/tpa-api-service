package com.bct.ngtpa.apiservice.application.usecase.accountswitch;

import com.bct.ngtpa.apiservice.application.dto.AccountContext;
import com.bct.ngtpa.apiservice.application.dto.TermStatus;
import com.bct.ngtpa.apiservice.application.dto.session.authenticated.AuthenticatedAccountRef;
import com.bct.ngtpa.apiservice.application.dto.session.authenticated.AuthenticatedSessionAccount;
import com.bct.ngtpa.apiservice.application.dto.session.authenticated.AuthenticatedSessionId;
import com.bct.ngtpa.apiservice.application.port.out.AccountSwitchLookupPort;
import com.bct.ngtpa.apiservice.application.usecase.session.authenticated.AuthenticatedPortalContextResolutionException;
import com.bct.ngtpa.apiservice.application.usecase.session.authenticated.AuthenticatedSessionLifecycleStatus;
import com.bct.ngtpa.apiservice.application.usecase.session.authenticated.LoadAndTouchAuthenticatedSessionCommand;
import com.bct.ngtpa.apiservice.application.usecase.session.authenticated.LoadAndTouchAuthenticatedSessionUseCase;
import java.util.Objects;
import reactor.core.publisher.Mono;

/**
 * Default implementation of {@link AccountSwitchLookupPort}.
 *
 * <p>Mirrors the same load-and-touch + {@code findAccount} mechanism used elsewhere for resolving
 * an account within an authenticated session, but as a standalone class with its own dedicated
 * port. It does not call, import, or otherwise depend on the web filter's external account/session
 * resolution port -- that port remains reserved for the inbound web filter boundary only, which is
 * unrelated to this switch-lookup capability.
 */
public final class AuthenticatedAccountSwitchLookupAdapter implements AccountSwitchLookupPort {

  private final LoadAndTouchAuthenticatedSessionUseCase lifecycle;

  public AuthenticatedAccountSwitchLookupAdapter(LoadAndTouchAuthenticatedSessionUseCase lifecycle) {
    this.lifecycle = Objects.requireNonNull(lifecycle);
  }

  @Override
  public Mono<AccountContext> resolveTargetAccount(String targetAccountRef, String sessionId) {
    if (blank(targetAccountRef) || blank(sessionId)) {
      return Mono.error(new AuthenticatedPortalContextResolutionException());
    }

    AuthenticatedSessionId id;
    AuthenticatedAccountRef ref;
    try {
      id = new AuthenticatedSessionId(sessionId.trim());
      ref = new AuthenticatedAccountRef(targetAccountRef.trim());
    } catch (RuntimeException malformed) {
      return Mono.error(new AuthenticatedPortalContextResolutionException());
    }

    return lifecycle
        .loadAndTouch(new LoadAndTouchAuthenticatedSessionCommand(id, null))
        .flatMap(
            result -> {
              if (result.status() != AuthenticatedSessionLifecycleStatus.ACTIVE
                  || result.session() == null) {
                return Mono.error(new AuthenticatedPortalContextResolutionException());
              }
              return result
                  .session()
                  .findAccount(ref)
                  .map(account -> Mono.just(toAccountContext(account)))
                  .orElseGet(() -> Mono.error(new AuthenticatedPortalContextResolutionException()));
            });
  }

  private AccountContext toAccountContext(AuthenticatedSessionAccount account) {
    return new AccountContext(
        account.accountRef().value(),
        account.accountEnv(),
        account.policyNo(),
        account.certNo(),
        account.trustCode(),
        account.schemeType(),
        TermStatus.fromCode(account.termStatus()),
        account.termCompletionDate());
  }

  private static boolean blank(String v) {
    return v == null || v.isBlank();
  }
}
