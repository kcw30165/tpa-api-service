package com.bct.ngtpa.apiservice.adapter.out.referencedate;

import com.bct.ngtpa.apiservice.adapter.out.configserver.ReferenceDateProperties;
import com.bct.ngtpa.apiservice.application.dto.ReferenceDateCacheUpdateCommand;
import com.bct.ngtpa.apiservice.application.port.out.ApimReferenceDateRefreshPort;
import com.bct.ngtpa.apiservice.application.port.out.CachePort;
import com.bct.ngtpa.apiservice.application.port.out.ReferenceDateCacheUpdatePort;
import com.bct.ngtpa.apiservice.application.port.out.ReferenceDatePort;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;
import reactor.core.publisher.Mono;

@Slf4j
public class NonpReferenceDateAdapter implements ReferenceDatePort {

    private static final DateTimeFormatter DATE_FORMATTER =
            DateTimeFormatter.ofPattern("dd/MM/uuuu").withResolverStyle(ResolverStyle.STRICT);
    private static final ZoneId HONG_KONG_ZONE = ZoneId.of("Asia/Hong_Kong");
    private static final String CACHE_KEY_SEGMENT = ":reference-date:";

    private final ReferenceDateProperties properties;
    private final Optional<CachePort> cachePort;
    private final String keyPrefix;
    private final ApimReferenceDateRefreshPort apimReferenceDateRefreshPort;
    private final ReferenceDateCacheUpdatePort referenceDateCacheUpdatePort;
    private final Environment environment;
    private final Clock clock;

    public NonpReferenceDateAdapter(
            ReferenceDateProperties properties,
            Optional<CachePort> cachePort,
            String keyPrefix,
            ApimReferenceDateRefreshPort apimReferenceDateRefreshPort,
            ReferenceDateCacheUpdatePort referenceDateCacheUpdatePort,
            Environment environment,
            Clock clock) {
        this.properties = properties;
        this.cachePort = cachePort;
        this.keyPrefix = keyPrefix;
        this.apimReferenceDateRefreshPort = apimReferenceDateRefreshPort;
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
                .switchIfEmpty(Mono.defer(() -> readFromApim(accountEnv)))
                .switchIfEmpty(Mono.fromSupplier(this::resolveHongKongSystemDate));
    }

    private Mono<LocalDate> readFromRedis(String accountEnv) {
        if (cachePort.isEmpty()) {
            return Mono.empty();
        }

        return cachePort.get().get(cacheKey(accountEnv))
                .flatMap(value -> parseOptionalDate(value.orElse(null),
                        () -> log.warn("reference-date: Redis cache value is not a valid date; falling through to APIM")))
                .onErrorResume(ex -> {
                    log.warn("reference-date: Redis cache read failed; falling through to APIM");
                    return Mono.empty();
                });
    }

    private Mono<LocalDate> readFromApim(String accountEnv) {
        return apimReferenceDateRefreshPort.fetchReferenceDate(accountEnv)
                .flatMap(date -> persistApimDate(accountEnv, date))
                .onErrorResume(ex -> {
                    log.warn("reference-date: APIM read failed; falling through to Hong Kong system date");
                    return Mono.empty();
                });
    }

    private Mono<LocalDate> persistApimDate(String accountEnv, LocalDate date) {
        String formattedDate = format(date);
        return updateRedisFromApim(accountEnv, formattedDate)
                .thenReturn(date)
                .onErrorResume(ex -> {
                    log.warn("reference-date: Redis update failed after APIM success; returning APIM date");
                    return Mono.just(date);
                });
    }

    private Mono<Void> updateRedisFromApim(String accountEnv, String formattedDate) {
        var cacheCommand = new ReferenceDateCacheUpdateCommand(cacheKey(accountEnv), formattedDate);
        return referenceDateCacheUpdatePort.updateReferenceDate(cacheCommand);
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
        return "PROD".equalsIgnoreCase(deploymentEnv)
                || "DR".equalsIgnoreCase(deploymentEnv);
    }

    private String cacheKey(String accountEnv) {
        return keyPrefix + CACHE_KEY_SEGMENT + accountEnv;
    }

    private String format(LocalDate date) {
        return DATE_FORMATTER.format(date);
    }
}