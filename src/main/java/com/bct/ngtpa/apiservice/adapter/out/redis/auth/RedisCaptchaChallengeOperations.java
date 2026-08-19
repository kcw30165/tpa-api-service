package com.bct.ngtpa.apiservice.adapter.out.redis.auth;

import java.time.Duration;
import java.util.Optional;
import reactor.core.publisher.Mono;

interface RedisCaptchaChallengeOperations {
    enum AtomicReplaceResult { APPLIED, CONFLICT, MISSING }
    Mono<Boolean> setIfAbsent(String key, String value, Duration ttl);
    default Mono<AtomicReplaceResult> replaceIfUnchanged(String key, String expectedValue, String replacementValue) {
        return get(key).flatMap(current -> {
            if (current.isEmpty()) return Mono.just(AtomicReplaceResult.MISSING);
            if (!current.get().equals(expectedValue)) return Mono.just(AtomicReplaceResult.CONFLICT);
            return ttl(key).flatMap(remaining -> remaining
                    .map(value -> set(key, replacementValue, value).thenReturn(AtomicReplaceResult.APPLIED))
                    .orElseGet(() -> Mono.just(AtomicReplaceResult.MISSING)));
        });
    }

    Mono<Void> set(String key, String value, Duration ttl);

    Mono<Optional<String>> get(String key);

    Mono<Optional<Duration>> ttl(String key);

    Mono<Void> delete(String key);
}