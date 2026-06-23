package com.bct.ngtpa.apiservice.adapter.out.referencedate;

import com.bct.ngtpa.apiservice.adapter.out.configserver.ReferenceDateProperties;
import com.bct.ngtpa.apiservice.application.dto.ReferenceDateCacheUpdateCommand;
import com.bct.ngtpa.apiservice.application.dto.RefreshReferenceDateCommand;
import com.bct.ngtpa.apiservice.application.port.out.ApimReferenceDateRefreshPort;
import com.bct.ngtpa.apiservice.application.port.out.CachePort;
import com.bct.ngtpa.apiservice.application.port.out.ReferenceDateCacheUpdatePort;
import com.bct.ngtpa.apiservice.application.usecase.RefreshReferenceDateService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class ReferenceDateRefreshReadConsistencyTest {

    private static final String REDIS_KEY_PREFIX = "ngtpa";
    private static final ZoneId UTC = ZoneId.of("UTC");
    private static final Clock FIXED_CLOCK = Clock.fixed(LocalDate.of(2026, 3, 15).atStartOfDay(UTC).toInstant(), UTC);

    @Test
    void refreshThenRead_redisHit_usesSameRedisKeyAndReturnsSameLocalDateWithoutTransformingAccountEnv() {
        String accountEnv = "jp-SIT_01";
        LocalDate refreshedDate = LocalDate.of(2026, 6, 30);
        var store = new SharedReferenceDateStore();

        var apimAccountEnv = new AtomicReference<String>();
        var cacheUpdate = new AtomicReference<ReferenceDateCacheUpdateCommand>();
        var redisReadKey = new AtomicReference<String>();
        var readerApimCalled = new AtomicBoolean(false);

        var refreshService = refreshService(
                accountEnvParam -> {
                    apimAccountEnv.set(accountEnvParam);
                    return Mono.just(refreshedDate);
                },
                command -> {
                    cacheUpdate.set(command);
                    store.cacheValues.put(command.cacheKey(), command.cacheValue());
                    return Mono.empty();
                });

        var reader = referenceDateReader(
                accountEnv,
                store,
                Optional.of(cachePort(store, redisReadKey)),
                accountEnvParam -> {
                    readerApimCalled.set(true);
                    return Mono.error(new IllegalStateException("APIM should not be called after refresh populated Redis"));
                },
                cacheCommand -> {
                    store.cacheValues.put(cacheCommand.cacheKey(), cacheCommand.cacheValue());
                    return Mono.empty();
                });

        StepVerifier.create(refreshService.execute(new RefreshReferenceDateCommand(accountEnv))
                .then(reader.resolveReferenceDate(accountEnv)))
                .assertNext(date -> assertThat(date).isEqualTo(refreshedDate))
                .verifyComplete();

        assertThat(apimAccountEnv.get()).isEqualTo(accountEnv);
        assertThat(cacheUpdate.get()).isEqualTo(new ReferenceDateCacheUpdateCommand(
                REDIS_KEY_PREFIX + ":reference-date:" + accountEnv,
                "30/06/2026"));
        assertThat(redisReadKey.get()).isEqualTo(REDIS_KEY_PREFIX + ":reference-date:" + accountEnv);
        assertThat(readerApimCalled.get()).isFalse();
    }

    @Test
    void readPath_redisMissThenApimHit_updatesRedisAndSubsequentReadUsesRedis() {
        String accountEnv = "sit-jp-lower";
        LocalDate refreshedDate = LocalDate.of(2025, 12, 31);
        var store = new SharedReferenceDateStore();
        var redisReadKey = new AtomicReference<String>();
        var apimCallCount = new AtomicReference<>(0);

        var reader = referenceDateReader(
                accountEnv,
                store,
                Optional.of(cachePort(store, redisReadKey)),
                accountEnvParam -> {
                    apimCallCount.set(apimCallCount.get() + 1);
                    return Mono.just(refreshedDate);
                },
                command -> {
                    store.cacheValues.put(command.cacheKey(), command.cacheValue());
                    return Mono.empty();
                });

        StepVerifier.create(reader.resolveReferenceDate(accountEnv).then(reader.resolveReferenceDate(accountEnv)))
                .expectNext(refreshedDate)
                .verifyComplete();

        assertThat(redisReadKey.get()).isEqualTo(REDIS_KEY_PREFIX + ":reference-date:" + accountEnv);
        assertThat(apimCallCount.get()).isEqualTo(1);
        assertThat(store.cacheValues)
                .containsEntry(REDIS_KEY_PREFIX + ":reference-date:" + accountEnv, "31/12/2025");
    }

    private RefreshReferenceDateService refreshService(
            ApimReferenceDateRefreshPort apimPort,
            ReferenceDateCacheUpdatePort cachePort) {
        return new RefreshReferenceDateService(apimPort, cachePort, REDIS_KEY_PREFIX);
    }

    private NonpReferenceDateAdapter referenceDateReader(
            String accountEnv,
            SharedReferenceDateStore stores,
            Optional<CachePort> cachePort,
            ApimReferenceDateRefreshPort apimPort,
            ReferenceDateCacheUpdatePort cacheUpdatePort) {
        var properties = new ReferenceDateProperties();
        properties.setAccountEnv(accountEnv);
        properties.setCacheTtlSeconds(3600);
        var environment = new MockEnvironment();
        environment.setActiveProfiles("SIT");
        return new NonpReferenceDateAdapter(
                properties,
                cachePort,
                REDIS_KEY_PREFIX,
                apimPort,
                cacheUpdatePort,
                environment,
                FIXED_CLOCK);
    }

    private CachePort cachePort(SharedReferenceDateStore stores, AtomicReference<String> lastGetKey) {
        return new CachePort() {
            @Override
            public Mono<Optional<String>> get(String cacheKey) {
                lastGetKey.set(cacheKey);
                return Mono.just(Optional.ofNullable(stores.cacheValues.get(cacheKey)));
            }

            @Override
            public Mono<Void> set(String cacheKey, String value, Duration ttl) {
                stores.cacheValues.put(cacheKey, value);
                return Mono.empty();
            }

            @Override
            public Mono<Boolean> evict(String cacheKey) {
                return Mono.just(stores.cacheValues.remove(cacheKey) != null);
            }
        };
    }

    private static final class SharedReferenceDateStore {
        private final Map<String, String> cacheValues = new HashMap<>();
    }
}