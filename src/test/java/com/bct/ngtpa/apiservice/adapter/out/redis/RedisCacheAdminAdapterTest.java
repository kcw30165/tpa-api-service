package com.bct.ngtpa.apiservice.adapter.out.redis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bct.ngtpa.apiservice.application.dto.CacheCapability;
import com.bct.ngtpa.apiservice.exception.CacheException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.reactivestreams.Publisher;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.ReactiveValueOperations;
import org.springframework.data.redis.core.ScanOptions;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@SuppressWarnings("unchecked")
class RedisCacheAdminAdapterTest {
    private ReactiveRedisTemplate<String, String> redisTemplate;
    private ReactiveValueOperations<String, String> valueOps;
    private RedisCacheAdminAdapter adapter;

    @BeforeEach
    void setUp() {
        redisTemplate = mock(ReactiveRedisTemplate.class);
        valueOps = mock(ReactiveValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        adapter = new RedisCacheAdminAdapter(redisTemplate, new RedisCacheKeyFactory("ngtpa"));
    }

    @Test
    void findAllByCapabilityScansCapabilityPatternAndReturnsEntries() {
        when(redisTemplate.scan(any(ScanOptions.class)))
                .thenReturn(Flux.just("ngtpa:reference-date:JP", "ngtpa:reference-date:HK"));
        when(valueOps.get("ngtpa:reference-date:JP")).thenReturn(Mono.just("31/12/2025"));
        when(valueOps.get("ngtpa:reference-date:HK")).thenReturn(Mono.just("01/01/2026"));

        StepVerifier.create(adapter.findAllByCapability(CacheCapability.REFERENCE_DATE))
                .assertNext(entry -> {
                    assertThat(entry.key()).isEqualTo("ngtpa:reference-date:JP");
                    assertThat(entry.capability()).isEqualTo("reference-date");
                    assertThat(entry.qualifier()).isEqualTo("JP");
                    assertThat(entry.value()).isEqualTo("31/12/2025");
                })
                .assertNext(entry -> {
                    assertThat(entry.qualifier()).isEqualTo("HK");
                    assertThat(entry.value()).isEqualTo("01/01/2026");
                })
                .verifyComplete();
    }

    @Test
    void findAllByCapabilityFiltersKeysWithoutQualifier() {
        when(redisTemplate.scan(any(ScanOptions.class)))
                .thenReturn(Flux.just("ngtpa:reference-date:", "ngtpa:reference-date:JP"));
        when(valueOps.get("ngtpa:reference-date:JP")).thenReturn(Mono.just("31/12/2025"));

        StepVerifier.create(adapter.findAllByCapability(CacheCapability.REFERENCE_DATE))
                .assertNext(entry -> assertThat(entry.qualifier()).isEqualTo("JP"))
                .verifyComplete();

        verify(valueOps, never()).get("ngtpa:reference-date:");
    }

    @Test
    void evictAllByCapabilityDeletesScannedKeysAndReturnsDeletedCount() {
        when(redisTemplate.scan(any(ScanOptions.class)))
                .thenReturn(Flux.just("ngtpa:reference-date:JP", "ngtpa:reference-date:HK"));
        when(valueOps.get("ngtpa:reference-date:JP")).thenReturn(Mono.just("31/12/2025"));
        when(valueOps.get("ngtpa:reference-date:HK")).thenReturn(Mono.just("01/01/2026"));
        when(redisTemplate.delete(any(Publisher.class))).thenReturn(Mono.just(2L));

        StepVerifier.create(adapter.evictAllByCapability(CacheCapability.REFERENCE_DATE))
                .expectNext(2L)
                .verifyComplete();

        verify(redisTemplate).delete(any(Publisher.class));
    }

    @Test
    void evictAllByCapabilityReturnsZeroWhenNoKeysExist() {
        when(redisTemplate.scan(any(ScanOptions.class))).thenReturn(Flux.empty());

        StepVerifier.create(adapter.evictAllByCapability(CacheCapability.REFERENCE_DATE))
                .expectNext(0L)
                .verifyComplete();

        verify(redisTemplate, never()).delete(any(Publisher.class));
    }

    @Test
    void rejectsNullCapabilityBeforeRedisCall() {
        StepVerifier.create(adapter.findAllByCapability(null))
                .expectError(IllegalArgumentException.class)
                .verify();

        verify(redisTemplate, never()).scan(any(ScanOptions.class));
    }

    @Test
    void mapsRedisScanFailureToCacheException() {
        when(redisTemplate.scan(any(ScanOptions.class)))
                .thenReturn(Flux.error(new RuntimeException("redis:6379 password=secret")));

        StepVerifier.create(adapter.findAllByCapability(CacheCapability.REFERENCE_DATE))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(CacheException.class);
                    assertThat(error.getMessage()).doesNotContain("secret");
                })
                .verify();
    }
}
