package com.bct.ngtpa.apiservice.adapter.out.referencedate;

import com.bct.ngtpa.apiservice.adapter.out.configserver.ReferenceDateProperties;
import com.bct.ngtpa.apiservice.application.dto.ConfigQuery;
import com.bct.ngtpa.apiservice.application.exception.InvalidContributionRequestException;
import com.bct.ngtpa.apiservice.application.port.out.CachePort;
import com.bct.ngtpa.apiservice.application.port.out.ConfigServicePort;
import com.bct.ngtpa.apiservice.application.port.out.ReferenceDatePort;
import com.bct.ngtpa.apiservice.exception.CacheException;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Optional;
import java.util.Set;

/**
 * Orchestrating implementation of {@link ReferenceDatePort} that consults
 * multiple date sources in a defined precedence order:
 *
 * <ol>
 *   <li>{@code reference-date.override-date} — when configured, returned
 *       immediately for non-production environments (skips Redis and Config
 *       Service).</li>
 *   <li>Redis cache (to be wired in a later prompt).</li>
 *   <li>Config Service (to be wired in a later prompt).</li>
 *   <li>System date — final fallback, using
 *       {@code reference-date.override-zone-id} as the time zone when
 *       configured, otherwise the injected {@link Clock}'s zone.</li>
 * </ol>
 *
 * <p>This class is intentionally free of Spring annotations. It is registered
 * as the {@link ReferenceDatePort} bean by
 * {@code ReferenceDateAdapterConfig} so that Redis / Config Service
 * dependencies can be made conditional without polluting this class.
 */
@Slf4j
public class OrchestratedReferenceDateAdapter implements ReferenceDatePort {

    private static final DateTimeFormatter DATE_FORMATTER =
            DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private static final Set<String> PRODUCTION_LIKE_ENVS =
            Set.of("PROD", "PRD", "PRODUCTION", "DR");

    private final ReferenceDateProperties properties;
    private final Optional<CachePort> cachePort;
    private final String keyPrefix;
    private final ConfigServicePort configServicePort;
    private final Clock clock;

    public OrchestratedReferenceDateAdapter(
            ReferenceDateProperties properties,
            Optional<CachePort> cachePort,
            String keyPrefix,
            ConfigServicePort configServicePort,
            Clock clock) {
        this.properties = properties;
        this.cachePort = cachePort;
        this.keyPrefix = keyPrefix;
        this.configServicePort = configServicePort;
        this.clock = clock;
    }

    @Override
    public Mono<LocalDate> resolveReferenceDate() {
        var accountEnv = properties.getAccountEnv();
        // ── Step 1: Override-date short-circuit (non-production envs only) ─────
        if (!isProductionLike(accountEnv) && hasText(properties.getOverrideDate())) {
            return Mono.fromCallable(() -> resolveOverrideDate(
                    properties.getOverrideDate(),
                    properties.getOverrideZoneId()));
        }

        // ── Steps 2 & 3: Redis → Config Service (Step 4: system-date fallback) ─
        return resolveFromDynamicSources(accountEnv)
                .switchIfEmpty(Mono.fromSupplier(this::resolveSystemDate));
    }

    // ── Dynamic source chain (Redis → Config Service) ─────────────────────────

    private Mono<LocalDate> resolveFromDynamicSources(String accountEnv) {
        return readFromRedis(accountEnv)
                .switchIfEmpty(Mono.defer(() -> readFromConfigService(accountEnv)));
    }

    /**
     * Attempts to read the reference date from Redis.
     *
     * <p>Returns {@link Mono#empty()} when:
     * <ul>
     *   <li>Redis is disabled (no {@link CachePort} bean).</li>
     *   <li>The key is absent in the cache.</li>
     *   <li>The cached value is not a valid {@code dd/MM/yyyy} date (WARN logged).</li>
     *   <li>A {@link CacheException} is thrown during the read (WARN logged).</li>
     * </ul>
     *
     * <p>In all fallthrough cases only a sanitized static message is logged;
     * raw connection details from {@link CacheException#getCause()} are never
     * emitted to the log.
     */
    private Mono<LocalDate> readFromRedis(String accountEnv) {
        if (cachePort.isEmpty()) {
            return Mono.empty();
        }
        String key = keyPrefix + ":reference-date:" + accountEnv;
        return cachePort.get().get(key)
                .flatMap(opt -> {
                    if (opt.isEmpty()) {
                        return Mono.<LocalDate>empty();
                    }
                    try {
                        return Mono.just(LocalDate.parse(opt.get(), DATE_FORMATTER));
                    } catch (DateTimeParseException ex) {
                        log.warn("reference-date: Redis cache value is not a valid date; treating as cache miss");
                        return Mono.<LocalDate>empty();
                    }
                })
                .onErrorResume(CacheException.class, ex -> {
                    log.warn("reference-date: Redis cache read failed; falling through to Config Service");
                    return Mono.empty();
                });
    }

    /**
     * Consults the Config Service for the reference date.
     *
     * <p>On a hit, the value is parsed as {@code dd/MM/yyyy} and Redis is populated
     * (when Redis is enabled and a positive TTL is configured).
     * Any Redis populate failure is logged with a sanitized warning and swallowed so
     * the Config Service date is still returned.
     *
     * <p>On a miss (empty list) or on any error, falls through by returning
     * {@link Mono#empty()} so the system-date fallback is reached.
     */
    private Mono<LocalDate> readFromConfigService(String accountEnv) {
        String configKey = "reference-date." + accountEnv;
        return configServicePort.listConfigs(new ConfigQuery(null, null, null, configKey))
                .flatMap(entries -> {
                    if (entries.isEmpty()) {
                        return Mono.<LocalDate>empty();
                    }
                    String rawDate = entries.get(0).configValue();
                    try {
                        LocalDate date = LocalDate.parse(rawDate, DATE_FORMATTER);
                        return populateRedis(accountEnv, rawDate).thenReturn(date);
                    } catch (DateTimeParseException ex) {
                        log.warn("reference-date: Config Service value for [{}] is not a valid date; falling through to system date", accountEnv);
                        return Mono.<LocalDate>empty();
                    }
                })
                .onErrorResume(ex -> {
                    log.warn("reference-date: Config Service read failed for [{}]; falling through to system date", accountEnv);
                    return Mono.empty();
                });
    }

    /**
     * Populates Redis with the given raw date string for the supplied environment.
     *
     * <p>Skipped when Redis is disabled (no {@link CachePort} bean) or when
     * {@code reference-date.cache-ttl-seconds} is zero or negative.
     * Any {@link CacheException} from the write is absorbed with a sanitized WARN
     * log so the caller always receives the Config Service date.
     */
    private Mono<Void> populateRedis(String accountEnv, String rawDate) {
        if (cachePort.isEmpty()) {
            return Mono.empty();
        }
        long ttlSeconds = properties.getCacheTtlSeconds();
        if (ttlSeconds <= 0) {
            return Mono.empty();
        }
        String key = keyPrefix + ":reference-date:" + accountEnv;
        return cachePort.get().set(key, rawDate, Duration.ofSeconds(ttlSeconds))
                .onErrorResume(CacheException.class, ex -> {
                    log.warn("reference-date: Redis populate failed; Config Service value will still be returned");
                    return Mono.empty();
                });
    }

    // ── Override-date resolution ──────────────────────────────────────────────

    private LocalDate resolveOverrideDate(String overrideDate, String overrideZoneId) {
        if (!hasText(overrideZoneId)) {
            throw new InvalidContributionRequestException(
                    "reference-date.override-date and reference-date.override-zone-id must be provided together");
        }
        validateZoneId(overrideZoneId);
        try {
            return LocalDate.parse(overrideDate, DATE_FORMATTER);
        } catch (DateTimeParseException ex) {
            throw new InvalidContributionRequestException(
                    "reference-date.override-date must use dd/MM/yyyy format");
        }
    }

    // ── System-date fallback ──────────────────────────────────────────────────

    /**
     * Returns today's date according to the injected {@link Clock}.
     *
     * <p>When {@code reference-date.override-zone-id} is configured and valid,
     * the clock's instant is interpreted in that time zone — making the result
     * deterministic in tests that inject a fixed clock.  If the zone ID is
     * absent or invalid, the clock's own zone is used as a safe default.
     */
    private LocalDate resolveSystemDate() {
        String overrideZoneId = properties.getOverrideZoneId();
        if (hasText(overrideZoneId)) {
            try {
                ZoneId zone = ZoneId.of(overrideZoneId);
                return clock.instant().atZone(zone).toLocalDate();
            } catch (Exception ignored) {
                // Invalid zone ID: fall through to the clock's own zone
            }
        }
        return LocalDate.now(clock);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private boolean isProductionLike(String accountEnv) {
        if (accountEnv == null || accountEnv.isBlank()) {
            return true;
        }
        return PRODUCTION_LIKE_ENVS.contains(accountEnv.toUpperCase());
    }

    private void validateZoneId(String zoneId) {
        try {
            ZoneId.of(zoneId);
        } catch (RuntimeException ex) {
            throw new InvalidContributionRequestException(
                    "reference-date.override-zone-id is invalid");
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
