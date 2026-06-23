package com.bct.ngtpa.apiservice.application.port.out;

import com.bct.ngtpa.apiservice.application.dto.CacheCapability;
import com.bct.ngtpa.apiservice.application.dto.CacheEntry;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Outbound port for capability-scoped cache administration.
 *
 * <p>This port intentionally exposes enum-based capability operations only. It
 * must not expose arbitrary Redis pattern deletion to application use cases.</p>
 */
public interface CacheAdminPort {
    Flux<CacheEntry> findAllByCapability(CacheCapability capability);

    Mono<Long> evictAllByCapability(CacheCapability capability);
}
