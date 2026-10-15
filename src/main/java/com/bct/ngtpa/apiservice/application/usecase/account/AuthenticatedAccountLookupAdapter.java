package com.bct.ngtpa.apiservice.application.usecase.account;

import com.bct.ngtpa.apiservice.application.dto.AccountContext;
import com.bct.ngtpa.apiservice.application.dto.TermStatus;
import com.bct.ngtpa.apiservice.application.dto.session.authenticated.AuthenticatedAccountRef;
import com.bct.ngtpa.apiservice.application.dto.session.authenticated.AuthenticatedSession;
import com.bct.ngtpa.apiservice.application.dto.session.authenticated.AuthenticatedSessionAccount;
import com.bct.ngtpa.apiservice.application.dto.session.authenticated.AuthenticatedSessionId;
import com.bct.ngtpa.apiservice.application.port.out.AccountLookupPort;
import com.bct.ngtpa.apiservice.application.usecase.session.authenticated.AuthenticatedPortalContextResolutionException;
import com.bct.ngtpa.apiservice.application.usecase.session.authenticated.AuthenticatedSessionLifecycleStatus;
import com.bct.ngtpa.apiservice.application.usecase.session.authenticated.LoadAndTouchAuthenticatedSessionCommand;
import com.bct.ngtpa.apiservice.application.usecase.session.authenticated.LoadAndTouchAuthenticatedSessionUseCase;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import reactor.core.publisher.Mono;

/**
 * Default implementation of {@link AccountLookupPort}.
 *
 * <p>Both {@link #getAccount} and {@link #listAccounts} share the same session load-and-touch
 * mechanism (one private helper, {@link #loadActiveSession(String)}) so neither duplicates the
 * session-resolution logic -- they differ only in how they then read {@code
 * AuthenticatedSession#accounts()}: a single-match filter versus the full list.
 */
public final class AuthenticatedAccountLookupAdapter implements AccountLookupPort {

  private final LoadAndTouchAuthenticatedSessionUseCase lifecycle;

  public AuthenticatedAccountLookupAdapter(LoadAndTouchAuthenticatedSessionUseCase lifecycle) {
    this.lifecycle = Objects.requireNonNull(lifecycle);
  }

  @Override
  public Mono<AccountContext> getAccount(String targetAccountRef, String sessionId) {
    if (blank(targetAccountRef)) {
      return Mono.error(new AuthenticatedPortalContextResolutionException());
    }
    AuthenticatedAccountRef ref;
    try {
      ref = new AuthenticatedAccountRef(targetAccountRef.trim());
    } catch (RuntimeException malformed) {
      return Mono.error(new AuthenticatedPortalContextResolutionException());
    }

    return loadActiveSession(sessionId)
        .flatMap(
            session ->
                session
                    .findAccount(ref)
                    .map(account -> Mono.just(toAccountContext(account)))
                    .orElseGet(() -> Mono.error(new AccountNotInSessionException())));
  }

  @Override
  public Mono<List<AccountContext>> listAccounts(String sessionId) {
    return loadActiveSession(sessionId)
        .map(
            session ->
                session.accounts().stream()
                    .map(this::toAccountContext)
                    .collect(Collectors.toList()));
  }

  private Mono<AuthenticatedSession> loadActiveSession(String sessionId) {
    if (blank(sessionId)) {
      return Mono.error(new AuthenticatedPortalContextResolutionException());
    }
    AuthenticatedSessionId id;
    try {
      id = new AuthenticatedSessionId(sessionId.trim());
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
              return Mono.just(result.session());
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
