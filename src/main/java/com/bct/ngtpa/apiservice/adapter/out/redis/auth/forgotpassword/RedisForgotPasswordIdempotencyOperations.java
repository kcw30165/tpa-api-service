package com.bct.ngtpa.apiservice.adapter.out.redis.auth.forgotpassword;

import java.time.Duration;
import java.util.Optional;
import reactor.core.publisher.Mono;

interface RedisForgotPasswordIdempotencyOperations {
    Mono<Boolean> setIfAbsent(String key, String value, Duration ttl);

    Mono<Void> set(String key, String value, Duration ttl);

    Mono<Optional<String>> get(String key);

    Mono<Void> delete(String key);
    default Mono<String> attachPendingCommitRef(
            String key,
            String requestHash,
            String opaqueCommitRef,
            Duration ttl) {
        return Mono.error(new UnsupportedOperationException("Pending commit reference operation is not configured"));
    }
}