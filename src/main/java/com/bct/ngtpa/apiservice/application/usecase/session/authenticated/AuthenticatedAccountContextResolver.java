package com.bct.ngtpa.apiservice.application.usecase.session.authenticated;

import com.bct.ngtpa.apiservice.application.dto.*;
import com.bct.ngtpa.apiservice.application.dto.session.authenticated.*;
import com.bct.ngtpa.apiservice.application.port.out.PortalAccessContextPort;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

public final class AuthenticatedAccountContextResolver implements PortalAccessContextPort {
  private static final Logger LOG =
      LoggerFactory.getLogger(AuthenticatedAccountContextResolver.class);

  private final LoadAndTouchAuthenticatedSessionUseCase lifecycle;

  public AuthenticatedAccountContextResolver(LoadAndTouchAuthenticatedSessionUseCase lifecycle) {
    this.lifecycle = Objects.requireNonNull(lifecycle);
  }

  public Mono<PortalAccessContext> resolvePortalAccessContext(String accountRef) {
    return Mono.error(new AuthenticatedPortalContextResolutionException());
  }

  public Mono<PortalAccessContext> resolvePortalAccessContext(String accountRef, String sessionId) {
    if (blank(accountRef) || blank(sessionId))
      return Mono.error(new AuthenticatedPortalContextResolutionException());
    AuthenticatedSessionId id;
    AuthenticatedAccountRef ref;
    try {
      id = new AuthenticatedSessionId(sessionId.trim());
      ref = new AuthenticatedAccountRef(accountRef.trim());
    } catch (RuntimeException e) {
      return Mono.defer(
          () -> {
            LOG.warn(
                "event=authenticated.context.failure stage=account-context-resolver reason=INVALID_SESSION_ID_OR_ACCOUNT_REF_FORMAT");
            return Mono.error(new AuthenticatedPortalContextResolutionException());
          });
    }
    return lifecycle
        .loadAndTouch(new LoadAndTouchAuthenticatedSessionCommand(id, null))
        .flatMap(
            result -> {
              if (result.status() != AuthenticatedSessionLifecycleStatus.ACTIVE
                  || result.session() == null) {
                LOG.warn(
                    "event=authenticated.context.failure stage=account-context-resolver reason=SESSION_NOT_ACTIVE lifecycleStatus={} sessionPresent={}",
                    result.status(),
                    result.session() != null);
                return Mono.error(new AuthenticatedPortalContextResolutionException());
              }
              return result
                  .session()
                  .findAccount(ref)
                  .map(account -> Mono.just(map(result.session(), account)))
                  .orElseGet(
                      () -> {
                        LOG.warn(
                            "event=authenticated.context.failure stage=account-context-resolver reason=ACCOUNT_REF_NOT_IN_SESSION accountCount={}",
                            result.session().accounts().size());
                        return Mono.error(new AuthenticatedPortalContextResolutionException());
                      });
            });
  }

  private PortalAccessContext map(
      AuthenticatedSession session, AuthenticatedSessionAccount account) {
    return new PortalAccessContext(
        session.actorContext(),
        new AccountContext(
            account.accountRef().value(),
            account.accountEnv(),
            account.policyNo(),
            account.certNo(),
            account.trustCode(),
            account.schemeType(),
            TermStatus.fromCode(account.termStatus()),
            account.termCompletionDate()));
  }

  private static boolean blank(String v) {
    return v == null || v.isBlank();
  }
}
