package com.bct.ngtpa.apiservice.adapter.out.redis.auth;

import java.time.Duration;
import java.util.Optional;
import reactor.core.publisher.Mono;

interface RedisCaptchaChallengeOperations {
    Mono<Boolean> setIfAbsent(String key, String value, Duration ttl);

    Mono<Void> set(String key, String value, Duration ttl);

    Mono<Optional<String>> get(String key);

    Mono<Optional<Duration>> ttl(String key);

    Mono<Void> delete(String key);
}