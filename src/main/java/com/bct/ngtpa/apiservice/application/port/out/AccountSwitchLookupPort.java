package com.bct.ngtpa.apiservice.application.port.out;

import com.bct.ngtpa.apiservice.application.dto.AccountContext;
import reactor.core.publisher.Mono;

/**
 * Outbound port dedicated to the account-switch capability: resolving a different account's display
 * metadata within the same authenticated session.
 *
 * <p>This is deliberately NOT {@link PortalAccessContextPort}. That port's {@code
 * resolvePortalAccessContext(accountRef, sessionId)} is architecture-guarded to be called only from
 * {@code PortalAccessContextWebFilter} (see {@code
 * PortalAccessContextResponsibilityBoundaryGuardTest} and {@code
 * PortalAccessContextFinalMigrationEvidenceTest}), which resolves the account bound to the
 * *current* request's {@code Account-Ref} header. The switch use case has a different need: an
 * explicit, user-commanded lookup of a *different* target account by ref, which the filter cannot
 * and must not be repurposed to provide. This port exists so that need is served without touching
 * {@code PortalAccessContextPort} or {@code PortalAccessContextWebFilter}.
 */
@FunctionalInterface
public interface AccountSwitchLookupPort {

  /**
   * Resolves the target account's {@link AccountContext} within the given authenticated session.
   *
   * @param targetAccountRef the account the user wants to switch to; must not be {@code null}
   * @param sessionId the current authenticated session id; must not be {@code null}
   * @return a {@link Mono} emitting the resolved {@link AccountContext}, or erroring with {@code
   *     AuthenticatedPortalContextResolutionException} if the session is invalid or the target
   *     account is not present in the session's account list
   */
  Mono<AccountContext> resolveTargetAccount(String targetAccountRef, String sessionId);
}
