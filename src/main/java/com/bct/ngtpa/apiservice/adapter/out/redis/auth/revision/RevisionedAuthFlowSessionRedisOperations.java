package com.bct.ngtpa.apiservice.adapter.out.redis.auth.revision;

import java.time.Duration;
import reactor.core.publisher.Mono;

public interface RevisionedAuthFlowSessionRedisOperations {
    Mono<Boolean> createIfAbsent(String sessionKey, String revisedJson, Duration ttl);
    Mono<Long> initializeLegacy(String sessionKey, String lockKey, String ownershipToken,
            String expectedLegacyJson, String revisedJson);
    Mono<Long> replaceIfCurrent(String sessionKey, String expectedJson, String revisedJson);
    Mono<Long> deleteIfCurrent(String sessionKey, String expectedJson);
}
