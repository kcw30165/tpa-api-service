package com.bct.ngtpa.apiservice.application.port.out;

import com.bct.ngtpa.apiservice.application.dto.PortalAccessContext;
import reactor.core.publisher.Mono;

/**
 * Provides the PortalAccessContext already associated with the current request.
 *
 * <p>This is a current-context accessor, not an external account/session resolver.
 * The PortalAccessContext is resolved earlier by PortalAccessContextWebFilter via
 * PortalAccessContextPort, then stored in Reactor Context for downstream code.</p>
 */
public interface CurrentPortalAccessContextResolver {

    /**
     * Returns the current request PortalAccessContext, or fails when the current
     * request does not have one.
     */
    Mono<PortalAccessContext> current();

    /**
     * Returns the current request PortalAccessContext, or Mono.empty() when the
     * current request does not have one.
     */
    Mono<PortalAccessContext> currentOrEmpty();
}
