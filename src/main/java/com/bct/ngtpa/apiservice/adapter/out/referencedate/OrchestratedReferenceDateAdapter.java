package com.bct.ngtpa.apiservice.adapter.out.referencedate;

import com.bct.ngtpa.apiservice.adapter.out.configserver.ReferenceDateProperties;
import com.bct.ngtpa.apiservice.application.exception.InvalidContributionRequestException;
import com.bct.ngtpa.apiservice.application.port.out.CachePort;
import com.bct.ngtpa.apiservice.application.port.out.ConfigServicePort;
import com.bct.ngtpa.apiservice.application.port.out.ReferenceDatePort;
import reactor.core.publisher.Mono;

import java.time.Clock;
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
public class OrchestratedReferenceDateAdapter implements ReferenceDatePort {

    private static final DateTimeFormatter DATE_FORMATTER =
            DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private static final Set<String> PRODUCTION_LIKE_ENVS =
            Set.of("PROD", "PRD", "PRODUCTION", "DR");

    private final ReferenceDateProperties properties;
    private final Optional<CachePort> cachePort;
    private final ConfigServicePort configServicePort;
    private final Clock clock;

    public OrchestratedReferenceDateAdapter(
            ReferenceDateProperties properties,
            Optional<CachePort> cachePort,
            ConfigServicePort configServicePort,
            Clock clock) {
        this.properties = properties;
        this.cachePort = cachePort;
        this.configServicePort = configServicePort;
        this.clock = clock;
    }

    @Override
    public Mono<LocalDate> resolveReferenceDate(String accountEnv) {
        // ── Step 1: Override-date short-circuit (non-production envs only) ─────
        if (!isProductionLike(accountEnv) && hasText(properties.getOverrideDate())) {
            return Mono.fromCallable(() -> resolveOverrideDate(
                    properties.getOverrideDate(),
                    properties.getOverrideZoneId()));
        }

        // ── Steps 2 & 3: Redis and Config Service (wired in later prompts) ─────

        // ── Step 4: System-date fallback ──────────────────────────────────────
        return Mono.fromSupplier(this::resolveSystemDate);
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
