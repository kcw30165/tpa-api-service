package com.bct.ngtpa.apiservice.application.port.out;

import com.bct.ngtpa.apiservice.application.dto.AccountContext;
import java.util.List;
import reactor.core.publisher.Mono;

/**
 * Outbound port for resolving account display metadata within the current authenticated session.
 *
 * <p>This is deliberately NOT the web filter's external account/session resolution port, which
 * remains reserved for the inbound web filter boundary only, resolving the account bound to the
 * *current* request's {@code Account-Ref} header. This port serves two distinct,
 * explicitly-requested lookups instead: listing every account in the session, and resolving one
 * specific account by ref -- neither of which the filter's port can serve.
 */
public interface AccountLookupPort {

  /**
   * Resolves one specific account's {@link AccountContext} within the given authenticated session.
   *
   * @param targetAccountRef the account to look up; must not be {@code null}
   * @param sessionId the current authenticated session id; must not be {@code null}
   * @return a {@link Mono} emitting the resolved {@link AccountContext}, or erroring with {@code
   *     AuthenticatedPortalContextResolutionException} if the session is invalid, or {@code
   *     AccountNotInSessionException} if the session is valid but the target account is not present
   *     in its account list
   */
  Mono<AccountContext> getAccount(String targetAccountRef, String sessionId);

  /**
   * Resolves every account's {@link AccountContext} within the given authenticated session.
   *
   * @param sessionId the current authenticated session id; must not be {@code null}
   * @return a {@link Mono} emitting the full list of resolved {@link AccountContext} (possibly
   *     empty), or erroring with {@code AuthenticatedPortalContextResolutionException} if the
   *     session is invalid
   */
  Mono<List<AccountContext>> listAccounts(String sessionId);
}
