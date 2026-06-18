package com.bct.ngtpa.apiservice.application.port.out;

import com.bct.ngtpa.apiservice.application.dto.PortalAccessContext;
import reactor.core.publisher.Mono;

/**
 * Outbound port for resolving the portal access context for a member account.
 *
 * <p>The clean target model is: {@code (auth-token context, accountRef) → PortalAccessContext}.
 * The {@code accountRef} parameter identifies which member account to resolve context for.
 *
 * <p><b>Transitional note:</b> Until auth-server integration is complete, callers that
 * do not yet receive {@code accountRef} from the HTTP layer should supply a well-known
 * config-key string (e.g. {@code "notifications"} or {@code "contributions"}) so that
 * transitional adapters can continue to resolve context from externalized configuration.
 * This parameter will become a real account reference once the auth layer is wired.
 */
@FunctionalInterface
public interface PortalAccessContextPort {

    /**
     * Resolves the full portal access context for the given account reference.
     *
     * @param accountRef identifies the member account; must not be {@code null}
     * @return a {@link Mono} that emits the resolved {@link PortalAccessContext},
     *         or signals an error if context cannot be resolved
     */
    Mono<PortalAccessContext> resolvePortalAccessContext(String accountRef);

    default Mono<PortalAccessContext> resolvePortalAccessContext(String accountRef, String sessionId) {
        return resolvePortalAccessContext(accountRef);
    }
}
