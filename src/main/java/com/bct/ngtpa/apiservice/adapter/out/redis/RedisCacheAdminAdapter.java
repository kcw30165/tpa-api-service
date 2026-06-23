package com.bct.ngtpa.apiservice.adapter.out.redis;

import com.bct.ngtpa.apiservice.application.dto.CacheCapability;
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
        public Flux<CacheEntry> findAllByCapability(CacheCapability capability) {
                return Flux.defer(() -> {
                        CacheCapability resolvedCapability = requireCapability(capability);
                        String capabilityKeyPart = resolvedCapability.keyPart();
                        String keyPrefix = redisCacheKeyFactory.capabilityPrefix(capabilityKeyPart) + ':';
                        ScanOptions scanOptions = ScanOptions.scanOptions()
                                        .match(redisCacheKeyFactory.patternForCapability(capabilityKeyPart))
                                        .build();

                        return redisTemplate.scan(scanOptions)
                                        .filter(key -> key != null && key.startsWith(keyPrefix)
                                                        && key.length() > keyPrefix.length())
                                        .flatMap(key -> redisTemplate.opsForValue()
                                                        .get(key)
                                                        .map(value -> new CacheEntry(
                                                                        key,
                                                                        capabilityKeyPart,
                                                                        key.substring(keyPrefix.length()),
                                                                        value)));
                }).onErrorMap(throwable -> !(throwable instanceof IllegalArgumentException)
                                && !(throwable instanceof CacheException),
                                throwable -> new CacheException("Cache admin scan operation failed", throwable));
        }

        @Override
        public Mono<Long> evictAllByCapability(CacheCapability capability) {
                return Mono.defer(() -> {
                        CacheCapability resolvedCapability = requireCapability(capability);
                        return findAllByCapability(capability)
                                        .map(CacheEntry::key)
                                        .collectList()
                                        .flatMap(keys -> keys.isEmpty()
                                                        ? Mono.just(0L)
                                                        : redisTemplate.delete(Flux.fromIterable(keys)));
                }).onErrorMap(throwable -> !(throwable instanceof IllegalArgumentException)
                                && !(throwable instanceof CacheException),
                                throwable -> new CacheException("Cache admin evict operation failed", throwable));
        }

        private CacheCapability requireCapability(CacheCapability capability) {
                if (capability == null) {
                        throw new IllegalArgumentException("Cache capability must not be null");
                }
                return capability;
        }
}