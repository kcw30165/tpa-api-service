package com.bct.ngtpa.apiservice.adapter.out.referencedate;

import com.bct.ngtpa.apiservice.adapter.out.configserver.ReferenceDateProperties;
import com.bct.ngtpa.apiservice.application.dto.ConfigEntry;
import com.bct.ngtpa.apiservice.application.dto.ConfigQuery;
import com.bct.ngtpa.apiservice.application.dto.ReferenceDateCacheUpdateCommand;
import com.bct.ngtpa.apiservice.application.dto.ReferenceDateConfigUpsertCommand;
import com.bct.ngtpa.apiservice.application.dto.RefreshReferenceDateCommand;
import com.bct.ngtpa.apiservice.application.port.out.ApimReferenceDateRefreshPort;
import com.bct.ngtpa.apiservice.application.port.out.CachePort;
import com.bct.ngtpa.apiservice.application.port.out.ConfigServicePort;
import com.bct.ngtpa.apiservice.application.port.out.ReferenceDateCacheUpdatePort;
import com.bct.ngtpa.apiservice.application.port.out.ReferenceDateConfigPort;
import com.bct.ngtpa.apiservice.application.usecase.RefreshReferenceDateService;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
        var stores = new SharedReferenceDateStores();

        var apimAccountEnv = new AtomicReference<String>();
        var configUpsert = new AtomicReference<ReferenceDateConfigUpsertCommand>();
        var cacheUpdate = new AtomicReference<ReferenceDateCacheUpdateCommand>();
        var redisReadKey = new AtomicReference<String>();

        var refreshService = refreshService(
                accountEnvParam -> {
                    apimAccountEnv.set(accountEnvParam);
                    return Mono.just(refreshedDate);
                },
                command -> {
                    configUpsert.set(command);
                    stores.configValues.put(command.configKey(), command.configValue());
                    return Mono.empty();
                },
                command -> {
                    cacheUpdate.set(command);
                    stores.cacheValues.put(command.cacheKey(), command.cacheValue());
                    return Mono.empty();
                });

        var reader = referenceDateReader(
                accountEnv,
                stores,
                Optional.of(cachePort(stores, redisReadKey)),
                configServicePort(stores, new AtomicReference<>()));

        StepVerifier.create(refreshService.execute(new RefreshReferenceDateCommand(accountEnv))
                .then(reader.resolveReferenceDate()))
                .assertNext(date -> assertThat(date).isEqualTo(refreshedDate))
                .verifyComplete();

        assertThat(apimAccountEnv.get()).isEqualTo(accountEnv);
        assertThat(configUpsert.get()).isEqualTo(new ReferenceDateConfigUpsertCommand(
                "reference-date." + accountEnv,
                "30/06/2026"));
        assertThat(cacheUpdate.get()).isEqualTo(new ReferenceDateCacheUpdateCommand(
                REDIS_KEY_PREFIX + ":reference-date:" + accountEnv,
                "30/06/2026"));
        assertThat(redisReadKey.get()).isEqualTo(REDIS_KEY_PREFIX + ":reference-date:" + accountEnv);
    }

    @Test
    void refreshThenRead_whenRedisSkipped_usesSameConfigKeyAndReturnsSameLocalDateWithoutTransformingAccountEnv() {
        String accountEnv = "sit-jp-lower";
        LocalDate refreshedDate = LocalDate.of(2025, 12, 31);
        var stores = new SharedReferenceDateStores();

        var apimAccountEnv = new AtomicReference<String>();
        var configUpsert = new AtomicReference<ReferenceDateConfigUpsertCommand>();
        var configQuery = new AtomicReference<ConfigQuery>();

        var refreshService = refreshService(
                accountEnvParam -> {
                    apimAccountEnv.set(accountEnvParam);
                    return Mono.just(refreshedDate);
                },
                command -> {
                    configUpsert.set(command);
                    stores.configValues.put(command.configKey(), command.configValue());
                    return Mono.empty();
                },
                command -> {
                    stores.cacheValues.put(command.cacheKey(), command.cacheValue());
                    return Mono.empty();
                });

        var reader = referenceDateReader(
                accountEnv,
                stores,
                Optional.empty(),
                configServicePort(stores, configQuery));

        StepVerifier.create(refreshService.execute(new RefreshReferenceDateCommand(accountEnv))
                .then(reader.resolveReferenceDate()))
                .assertNext(date -> assertThat(date).isEqualTo(refreshedDate))
                .verifyComplete();

        assertThat(apimAccountEnv.get()).isEqualTo(accountEnv);
        assertThat(configUpsert.get()).isEqualTo(new ReferenceDateConfigUpsertCommand(
                "reference-date." + accountEnv,
                "31/12/2025"));
        assertThat(configQuery.get()).isEqualTo(new ConfigQuery(null, null, null, "reference-date." + accountEnv));
    }

    private RefreshReferenceDateService refreshService(
            ApimReferenceDateRefreshPort apimPort,
            ReferenceDateConfigPort configPort,
            ReferenceDateCacheUpdatePort cachePort) {
        return new RefreshReferenceDateService(apimPort, configPort, cachePort, REDIS_KEY_PREFIX);
    }

    private OrchestratedReferenceDateAdapter referenceDateReader(
            String accountEnv,
            SharedReferenceDateStores stores,
            Optional<CachePort> cachePort,
            ConfigServicePort configServicePort) {
        var properties = new ReferenceDateProperties();
        properties.setAccountEnv(accountEnv);
        properties.setCacheTtlSeconds(3600);
        return new OrchestratedReferenceDateAdapter(properties, cachePort, REDIS_KEY_PREFIX, configServicePort,
                FIXED_CLOCK);
    }

    private CachePort cachePort(SharedReferenceDateStores stores, AtomicReference<String> lastGetKey) {
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

    private ConfigServicePort configServicePort(SharedReferenceDateStores stores,
            AtomicReference<ConfigQuery> lastQuery) {
        return new ConfigServicePort() {
            @Override
            public Mono<List<ConfigEntry>> listConfigs(ConfigQuery query) {
                lastQuery.set(query);
                String value = stores.configValues.get(query.configKey());
                if (value == null) {
                    return Mono.just(List.of());
                }
                return Mono.just(List.of(new ConfigEntry(null, null, null, query.configKey(), value)));
            }

            @Override
            public Mono<ConfigEntry> upsertConfig(
                    com.bct.ngtpa.apiservice.application.dto.ConfigUpsertCommand command) {
                throw new UnsupportedOperationException("Not used in this test");
            }

            @Override
            public Mono<Void> deleteConfig(String application, String profile, String label, String configKey) {
                throw new UnsupportedOperationException("Not used in this test");
            }
        };
    }

    private static final class SharedReferenceDateStores {
        private final Map<String, String> configValues = new HashMap<>();
        private final Map<String, String> cacheValues = new HashMap<>();
    }
}