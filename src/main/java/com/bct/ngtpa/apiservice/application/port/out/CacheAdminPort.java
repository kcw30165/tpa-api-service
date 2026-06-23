package com.bct.ngtpa.apiservice.application.port.out;

import com.bct.ngtpa.apiservice.application.dto.CacheEntry;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Outbound port for capability-scoped cache administration.
 *
 * <p>This port intentionally exposes capability-level operations only. It must
 * not expose arbitrary Redis pattern deletion to application use cases.</p>
 */
public interface CacheAdminPort {
    Flux<CacheEntry> findAllByCapability(String capability);

    Mono<Long> evictAllByCapability(String capability);
}
