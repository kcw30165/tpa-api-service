package com.bct.ngtpa.apiservice.adapter.out.referencedate;

import com.bct.ngtpa.apiservice.adapter.out.configserver.ReferenceDateProperties;
import com.bct.ngtpa.apiservice.application.dto.ConfigEntry;
import com.bct.ngtpa.apiservice.application.dto.ConfigQuery;
import com.bct.ngtpa.apiservice.application.dto.ReferenceDateCacheUpdateCommand;
import com.bct.ngtpa.apiservice.application.dto.ReferenceDateConfigUpsertCommand;
import com.bct.ngtpa.apiservice.application.port.out.ApimReferenceDateRefreshPort;
import com.bct.ngtpa.apiservice.application.port.out.CachePort;
import com.bct.ngtpa.apiservice.application.port.out.ConfigServicePort;
import com.bct.ngtpa.apiservice.application.port.out.ReferenceDateCacheUpdatePort;
import com.bct.ngtpa.apiservice.application.port.out.ReferenceDateConfigPort;
import com.bct.ngtpa.apiservice.application.port.out.ReferenceDatePort;
import com.bct.ngtpa.apiservice.exception.CacheException;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;
import reactor.core.publisher.Mono;

@Slf4j
public class NonpReferenceDateAdapter implements ReferenceDatePort {

    private static final DateTimeFormatter DATE_FORMATTER =
            DateTimeFormatter.ofPattern("dd/MM/uuuu").withResolverStyle(ResolverStyle.STRICT);
    private static final ZoneId HONG_KONG_ZONE = ZoneId.of("Asia/Hong_Kong");
    private static final String CONFIG_KEY_PREFIX = "reference-date.";
    private static final String CACHE_KEY_SEGMENT = ":reference-date:";

    private final ReferenceDateProperties properties;
    private final Optional<CachePort> cachePort;
    private final String keyPrefix;
    private final ConfigServicePort configServicePort;
    private final ApimReferenceDateRefreshPort apimReferenceDateRefreshPort;
    private final ReferenceDateConfigPort referenceDateConfigPort;
    private final ReferenceDateCacheUpdatePort referenceDateCacheUpdatePort;
    private final Environment environment;
    private final Clock clock;

    public NonpReferenceDateAdapter(
            ReferenceDateProperties properties,
            Optional<CachePort> cachePort,
            String keyPrefix,
            ConfigServicePort configServicePort,
            ApimReferenceDateRefreshPort apimReferenceDateRefreshPort,
            ReferenceDateConfigPort referenceDateConfigPort,
            ReferenceDateCacheUpdatePort referenceDateCacheUpdatePort,
            Environment environment,
            Clock clock) {
        this.properties = properties;
        this.cachePort = cachePort;
        this.keyPrefix = keyPrefix;
        this.configServicePort = configServicePort;
        this.apimReferenceDateRefreshPort = apimReferenceDateRefreshPort;
        this.referenceDateConfigPort = referenceDateConfigPort;
        this.referenceDateCacheUpdatePort = referenceDateCacheUpdatePort;
        this.environment = environment;
        this.clock = clock;
    }

    @Override
    public Mono<LocalDate> resolveReferenceDate() {
        if (isProductionLikeDeploymentEnv(currentDeploymentEnv())) {
            return Mono.fromSupplier(this::resolveHongKongSystemDate);
        }

        String accountEnv = properties.getAccountEnv();
        return readFromRedis(accountEnv)
                .switchIfEmpty(Mono.defer(() -> readFromConfigService(accountEnv)))
                .switchIfEmpty(Mono.defer(() -> readFromApim(accountEnv)))
                .switchIfEmpty(Mono.fromSupplier(this::resolveHongKongSystemDate));
    }

    private Mono<LocalDate> readFromRedis(String accountEnv) {
        if (cachePort.isEmpty()) {
            return Mono.empty();
        }

        return cachePort.get().get(cacheKey(accountEnv))
                .flatMap(value -> parseOptionalDate(value.orElse(null),
                        () -> log.warn("reference-date: Redis cache value is not a valid date; falling through to Config Service")))
                .onErrorResume(ex -> {
                    log.warn("reference-date: Redis cache read failed; falling through to Config Service");
                    return Mono.empty();
                });
    }

    private Mono<LocalDate> readFromConfigService(String accountEnv) {
        return configServicePort.listConfigs(new ConfigQuery(null, null, null, configKey(accountEnv)))
                .flatMap(entries -> readConfigEntry(accountEnv, entries))
                .onErrorResume(ex -> {
                    log.warn("reference-date: Config Service read failed for [{}]; falling through to APIM", accountEnv);
                    return Mono.empty();
                });
    }

    private Mono<LocalDate> readConfigEntry(String accountEnv, List<ConfigEntry> entries) {
        if (entries.isEmpty()) {
            return Mono.empty();
        }

        return parseOptionalDate(entries.getFirst().configValue(),
                () -> log.warn("reference-date: Config Service value for [{}] is not a valid date; falling through to APIM", accountEnv))
                .flatMap(date -> populateRedisFromConfigService(accountEnv, format(date)).thenReturn(date));
    }

    private Mono<LocalDate> readFromApim(String accountEnv) {
        return apimReferenceDateRefreshPort.fetchReferenceDate(accountEnv)
                .flatMap(date -> persistApimDate(accountEnv, date))
                .onErrorResume(ex -> {
                    log.warn("reference-date: APIM read failed for [{}]; falling through to Hong Kong system date", accountEnv);
                    return Mono.empty();
                });
    }

    private Mono<LocalDate> persistApimDate(String accountEnv, LocalDate date) {
        String formattedDate = format(date);
        var configCommand = new ReferenceDateConfigUpsertCommand(configKey(accountEnv), formattedDate);

        return referenceDateConfigPort.upsertReferenceDate(configCommand)
                .then(Mono.defer(() -> updateRedisFromApim(accountEnv, formattedDate)))
                .thenReturn(date)
                .onErrorResume(ex -> {
                    log.warn("reference-date: Config Service update failed after APIM success for [{}]; returning APIM date", accountEnv);
                    return Mono.just(date);
                });
    }

    private Mono<Void> populateRedisFromConfigService(String accountEnv, String formattedDate) {
        if (cachePort.isEmpty()) {
            return Mono.empty();
        }

        long ttlSeconds = properties.getCacheTtlSeconds();
        if (ttlSeconds <= 0) {
            return Mono.empty();
        }

        return cachePort.get().set(cacheKey(accountEnv), formattedDate, Duration.ofSeconds(ttlSeconds))
                .onErrorResume(ex -> {
                    log.warn("reference-date: Redis populate failed after Config Service hit; returning Config Service date");
                    return Mono.empty();
                });
    }

    private Mono<Void> updateRedisFromApim(String accountEnv, String formattedDate) {
        var cacheCommand = new ReferenceDateCacheUpdateCommand(cacheKey(accountEnv), formattedDate);
        return referenceDateCacheUpdatePort.updateReferenceDate(cacheCommand)
                .onErrorResume(ex -> {
                    log.warn("reference-date: Redis update failed after APIM success; returning APIM date");
                    return Mono.empty();
                });
    }

    private Mono<LocalDate> parseOptionalDate(String rawDate, Runnable invalidLogger) {
        if (rawDate == null || rawDate.isBlank()) {
            return Mono.empty();
        }

        try {
            return Mono.just(LocalDate.parse(rawDate.trim(), DATE_FORMATTER));
        } catch (DateTimeParseException ex) {
            invalidLogger.run();
            return Mono.empty();
        }
    }

    private LocalDate resolveHongKongSystemDate() {
        return clock.instant().atZone(HONG_KONG_ZONE).toLocalDate();
    }

    private String currentDeploymentEnv() {
        String[] activeProfiles = environment.getActiveProfiles();
        if (activeProfiles.length == 0) {
            return "";
        }
        return activeProfiles[0];
    }

    private boolean isProductionLikeDeploymentEnv(String deploymentEnv) {
        if (deploymentEnv == null || deploymentEnv.isBlank()) {
            return true;
        }

        return "PROD".equalsIgnoreCase(deploymentEnv)
                || "PRD".equalsIgnoreCase(deploymentEnv)
                || "PRODUCTION".equalsIgnoreCase(deploymentEnv)
                || "DR".equalsIgnoreCase(deploymentEnv);
    }

    private String cacheKey(String accountEnv) {
        return keyPrefix + CACHE_KEY_SEGMENT + accountEnv;
    }

    private String configKey(String accountEnv) {
        return CONFIG_KEY_PREFIX + accountEnv;
    }

    private String format(LocalDate date) {
        return DATE_FORMATTER.format(date);
    }
}