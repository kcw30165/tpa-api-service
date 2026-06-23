package com.bct.ngtpa.apiservice.adapter.out.redis;

import com.bct.ngtpa.apiservice.application.dto.CacheEntry;
import com.bct.ngtpa.apiservice.application.port.out.CacheAdminPort;
import com.bct.ngtpa.apiservice.exception.CacheException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RequiredArgsConstructor
public class RedisCacheAdminAdapter implements CacheAdminPort {
    private final ReactiveRedisTemplate<String, String> redisTemplate;
    private final RedisCacheKeyFactory redisCacheKeyFactory;

    @Override
    public Flux<CacheEntry> findAllByCapability(String capability) {
        String normalizedCapability = normalizeCapability(capability);
        String keyPrefix = redisCacheKeyFactory.capabilityPrefix(normalizedCapability) + ':';
        ScanOptions scanOptions = ScanOptions.scanOptions()
                .match(redisCacheKeyFactory.patternForCapability(normalizedCapability))
                .build();

        return redisTemplate.scan(scanOptions)
                .filter(key -> key != null && key.startsWith(keyPrefix) && key.length() > keyPrefix.length())
                .flatMap(key -> redisTemplate.opsForValue()
                        .get(key)
                        .map(value -> new CacheEntry(
                                key,
                                normalizedCapability,
                                key.substring(keyPrefix.length()),
                                value)))
                .onErrorMap(throwable -> throwable instanceof CacheException
                        ? throwable
                        : new CacheException("Cache admin scan operation failed", throwable));
    }

    @Override
    public Mono<Long> evictAllByCapability(String capability) {
        String normalizedCapability = normalizeCapability(capability);
        return findAllByCapability(normalizedCapability)
                .map(CacheEntry::key)
                .collectList()
                .flatMap(keys -> keys.isEmpty()
                        ? Mono.just(0L)
                        : redisTemplate.delete(Flux.fromIterable(keys)))
                .onErrorMap(throwable -> throwable instanceof CacheException
                        ? throwable
                        : new CacheException("Cache admin evict operation failed", throwable));
    }

    private String normalizeCapability(String capability) {
        if (!StringUtils.hasText(capability)) {
            throw new IllegalArgumentException("Cache capability must not be blank");
        }
        return capability.trim();
    }
}