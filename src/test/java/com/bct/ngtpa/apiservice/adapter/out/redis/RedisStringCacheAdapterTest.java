package com.bct.ngtpa.apiservice.adapter.out.redis;

import com.bct.ngtpa.apiservice.exception.CacheException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.ReactiveValueOperations;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link RedisStringCacheAdapter}.
 *
 * <p>All Redis interactions use a mocked {@link ReactiveRedisTemplate} — no real Redis
 * connection is made.
 */
@SuppressWarnings("unchecked")
class RedisStringCacheAdapterTest {

    private ReactiveRedisTemplate<String, String> redisTemplate;
    private ReactiveValueOperations<String, String> valueOps;
    private RedisStringCacheAdapter adapter;

    @BeforeEach
    void setUp() {
        redisTemplate = mock(ReactiveRedisTemplate.class);
        valueOps = mock(ReactiveValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        adapter = new RedisStringCacheAdapter(redisTemplate);
    }

    // ── get ───────────────────────────────────────────────────────────────────

    @Test
    void getReturnsEmptyOptionalWhenRedisHasNoValue() {
        when(valueOps.get("ngtpa:reference-date:JP")).thenReturn(Mono.empty());

        StepVerifier.create(adapter.get("ngtpa:reference-date:JP"))
                .assertNext(result -> assertThat(result).isEmpty())
                .verifyComplete();
    }

    @Test
    void getReturnsOptionalWithValueWhenRedisHasValue() {
        when(valueOps.get("ngtpa:reference-date:JP")).thenReturn(Mono.just("2025-01-15"));

        StepVerifier.create(adapter.get("ngtpa:reference-date:JP"))
                .assertNext(result -> assertThat(result).contains("2025-01-15"))
                .verifyComplete();
    }

    @Test
    void getRejectsBlankKey() {
        StepVerifier.create(adapter.get("  "))
                .expectError(IllegalArgumentException.class)
                .verify();

        verifyNoInteractions(redisTemplate);
    }

    @Test
    void getRejectsNullKey() {
        StepVerifier.create(adapter.get(null))
                .expectError(IllegalArgumentException.class)
                .verify();

        verifyNoInteractions(redisTemplate);
    }

    @Test
    void getMapsThrownRedisErrorToCacheException() {
        when(valueOps.get("ngtpa:reference-date:JP"))
                .thenReturn(Mono.error(new RuntimeException("redis:6379 connection refused password=secret")));

        StepVerifier.create(adapter.get("ngtpa:reference-date:JP"))
                .expectErrorSatisfies(err -> {
                    assertThat(err).isInstanceOf(CacheException.class);
                    assertThat(err.getMessage())
                            .doesNotContain("password")
                            .doesNotContain("secret")
                            .doesNotContain("6379");
                })
                .verify();
    }

    // ── set ───────────────────────────────────────────────────────────────────

    @Test
    void setWritesValueWithTtlToRedis() {
        Duration ttl = Duration.ofMinutes(10);
        when(valueOps.set("ngtpa:reference-date:JP", "2025-01-15", ttl))
                .thenReturn(Mono.just(true));

        StepVerifier.create(adapter.set("ngtpa:reference-date:JP", "2025-01-15", ttl))
                .verifyComplete();

        verify(valueOps).set("ngtpa:reference-date:JP", "2025-01-15", ttl);
    }

    @Test
    void setRejectsBlankKey() {
        StepVerifier.create(adapter.set("", "value", Duration.ofSeconds(10)))
                .expectError(IllegalArgumentException.class)
                .verify();

        verifyNoInteractions(redisTemplate);
    }

    @Test
    void setRejectsNullKey() {
        StepVerifier.create(adapter.set(null, "2025-01-15", Duration.ofSeconds(10)))
                .expectError(IllegalArgumentException.class)
                .verify();

        verifyNoInteractions(redisTemplate);
    }

    @Test
    void setRejectsNullTtl() {
        StepVerifier.create(adapter.set("ngtpa:reference-date:JP", "2025-01-15", null))
                .expectError(IllegalArgumentException.class)
                .verify();

        verifyNoInteractions(redisTemplate);
    }

    @Test
    void setRejectsZeroTtl() {
        StepVerifier.create(adapter.set("ngtpa:reference-date:JP", "2025-01-15", Duration.ZERO))
                .expectError(IllegalArgumentException.class)
                .verify();

        verifyNoInteractions(redisTemplate);
    }

    @Test
    void setRejectsNegativeTtl() {
        StepVerifier.create(adapter.set("ngtpa:reference-date:JP", "2025-01-15", Duration.ofSeconds(-1)))
                .expectError(IllegalArgumentException.class)
                .verify();

        verifyNoInteractions(redisTemplate);
    }

    @Test
    void setMapsThrownRedisErrorToCacheException() {
        Duration ttl = Duration.ofMinutes(5);
        when(valueOps.set("ngtpa:reference-date:JP", "2025-01-15", ttl))
                .thenReturn(Mono.error(new RuntimeException("tls cert error /path/to/redis-mtls.pem")));

        StepVerifier.create(adapter.set("ngtpa:reference-date:JP", "2025-01-15", ttl))
                .expectErrorSatisfies(err -> {
                    assertThat(err).isInstanceOf(CacheException.class);
                    assertThat(err.getMessage())
                            .doesNotContain("cert")
                            .doesNotContain("/path/to");
                })
                .verify();
    }

    // ── evict ─────────────────────────────────────────────────────────────────

    @Test
    void evictReturnsTrueWhenKeyWasDeleted() {
        doReturn(Mono.just(1L)).when(redisTemplate).delete("ngtpa:reference-date:JP");

        StepVerifier.create(adapter.evict("ngtpa:reference-date:JP"))
                .expectNext(true)
                .verifyComplete();
    }

    @Test
    void evictReturnsFalseWhenKeyDidNotExist() {
        doReturn(Mono.just(0L)).when(redisTemplate).delete("ngtpa:reference-date:JP");

        StepVerifier.create(adapter.evict("ngtpa:reference-date:JP"))
                .expectNext(false)
                .verifyComplete();
    }

    @Test
    void evictRejectsBlankKey() {
        StepVerifier.create(adapter.evict(""))
                .expectError(IllegalArgumentException.class)
                .verify();

        verifyNoInteractions(redisTemplate);
    }

    @Test
    void evictRejectsNullKey() {
        StepVerifier.create(adapter.evict(null))
                .expectError(IllegalArgumentException.class)
                .verify();

        verifyNoInteractions(redisTemplate);
    }

    @Test
    void evictMapsThrownRedisErrorToCacheException() {
        doReturn(Mono.error(new RuntimeException("sentinel:26379 auth failure host=redis.cluster.svc")))
                .when(redisTemplate).delete("ngtpa:reference-date:JP");

        StepVerifier.create(adapter.evict("ngtpa:reference-date:JP"))
                .expectErrorSatisfies(err -> {
                    assertThat(err).isInstanceOf(CacheException.class);
                    assertThat(err.getMessage())
                            .doesNotContain("auth")
                            .doesNotContain("host=")
                            .doesNotContain("26379");
                })
                .verify();
    }
}
