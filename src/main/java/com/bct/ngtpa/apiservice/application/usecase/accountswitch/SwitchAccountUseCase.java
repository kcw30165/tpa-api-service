package com.bct.ngtpa.apiservice.application.usecase.accountswitch;

import com.bct.ngtpa.apiservice.application.dto.AccountContext;
import reactor.core.publisher.Mono;

/**
 * Resolves display metadata for a different account within the same authenticated session.
 *
 * <p>Stateless: no session mutation occurs. On success, emits the target account's {@link
 * AccountContext}. If the target account is not present in the session's account list, the returned
 * {@link Mono} errors with {@code AuthenticatedPortalContextResolutionException}
 * (application.usecase.session.authenticated), matching the same contract used by {@code
 * AuthenticatedAccountContextResolver} for the identical underlying lookup. Callers do not need to
 * catch this locally -- the existing {@code AuthenticatedPortalContextWebExceptionHandler} already
 * maps it to 401 UNAUTHORIZED globally.
 */
public interface SwitchAccountUseCase {
  Mono<AccountContext> switchAccount(SwitchAccountCommand command);
}
