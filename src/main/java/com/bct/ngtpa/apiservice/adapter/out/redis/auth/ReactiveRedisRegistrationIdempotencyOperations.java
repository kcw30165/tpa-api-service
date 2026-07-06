package com.bct.ngtpa.apiservice.adapter.out.redis.auth;

import java.time.Duration;
import java.util.Optional;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import reactor.core.publisher.Mono;

class ReactiveRedisRegistrationIdempotencyOperations implements RedisRegistrationIdempotencyOperations {
    private final ReactiveStringRedisTemplate redisTemplate;

    ReactiveRedisRegistrationIdempotencyOperations(ReactiveStringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public Mono<Boolean> setIfAbsent(String key, String value, Duration ttl) {
        return redisTemplate.opsForValue().setIfAbsent(key, value, ttl).map(Boolean.TRUE::equals);
    }

    @Override
    public Mono<Void> set(String key, String value, Duration ttl) {
        return redisTemplate.opsForValue().set(key, value, ttl).then();
    }

    @Override
    public Mono<Optional<String>> get(String key) {
        return redisTemplate.opsForValue().get(key).map(Optional::of).defaultIfEmpty(Optional.empty());
    }

    @Override
    public Mono<Void> delete(String key) {
        return redisTemplate.delete(key).then();
    }
}
