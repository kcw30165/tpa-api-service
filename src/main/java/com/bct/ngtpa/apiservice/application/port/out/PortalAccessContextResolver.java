package com.bct.ngtpa.apiservice.application.port.out;

import com.bct.ngtpa.apiservice.application.dto.PortalAccessContext;
import reactor.core.publisher.Mono;

/**
 * Resolves the PortalAccessContext already associated with the current request.
 *
 * <p>The source is intentionally abstract. During the temporary phase it is populated
 * from YAML-backed Account-Ref resolution. In the final flow it will be populated from
 * session-id cookie + Account-Ref header after Redis session validation.</p>
 */
public interface PortalAccessContextResolver {

    Mono<PortalAccessContext> current();

    Mono<PortalAccessContext> currentOrEmpty();
}
