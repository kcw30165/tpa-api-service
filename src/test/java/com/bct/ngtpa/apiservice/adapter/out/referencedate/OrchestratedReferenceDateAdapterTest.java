package com.bct.ngtpa.apiservice.adapter.out.referencedate;

import com.bct.ngtpa.apiservice.adapter.out.configserver.ReferenceDateProperties;
import com.bct.ngtpa.apiservice.application.exception.InvalidContributionRequestException;
import com.bct.ngtpa.apiservice.application.port.out.CachePort;
import com.bct.ngtpa.apiservice.application.port.out.ConfigServicePort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import reactor.test.StepVerifier;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.bct.ngtpa.apiservice.application.dto.ConfigEntry;
import com.bct.ngtpa.apiservice.exception.CacheException;
import java.util.List;
import java.util.concurrent.TimeoutException;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

/**
 * Unit tests for {@link OrchestratedReferenceDateAdapter} covering the
 * override-date short-circuit (top of the source chain) and system-date
 * fallback (bottom of the source chain).
 *
 * <p>Redis and Config Service are injected as mocks. For each test in this
 * suite those mocks are never invoked — they exist only to satisfy the
 * constructor and to allow {@code verifyNoInteractions} where appropriate.
 * Prompts 04 and 05 will add tests for those paths.
 */
class OrchestratedReferenceDateAdapterTest {

    private static final ZoneId UTC = ZoneId.of("UTC");

    /**
     * A fixed clock anchored at 2026-03-15 00:00:00 UTC.
     * Used as the injected time source for deterministic date assertions.
     */
    private static final LocalDate FIXED_DATE = LocalDate.of(2026, 3, 15);
    private static final Clock FIXED_CLOCK =
            Clock.fixed(FIXED_DATE.atStartOfDay(UTC).toInstant(), UTC);

    // ── Helpers ───────────────────────────────────────────────────────────────


    private OrchestratedReferenceDateAdapter adapter(String accountEnv, String overrideDate, String overrideZoneId) {
        var configServicePort = mock(ConfigServicePort.class);
        when(configServicePort.listConfigs(any())).thenReturn(Mono.just(List.of()));
        return adapter(accountEnv, overrideDate, overrideZoneId, Optional.empty(), configServicePort);
    }

    private OrchestratedReferenceDateAdapter adapter(
            String accountEnv,
            String overrideDate,
            String overrideZoneId,
            Optional<CachePort> cachePort,
            ConfigServicePort configServicePort) {
        var props = new ReferenceDateProperties();
        props.setAccountEnv(accountEnv);
        props.setOverrideDate(overrideDate);
        props.setOverrideZoneId(overrideZoneId);
        return new OrchestratedReferenceDateAdapter(props, cachePort, "ngtpa", configServicePort, FIXED_CLOCK);
    }

    // ── Override-date short-circuit ───────────────────────────────────────────

    @Test
    void overrideDateAndZoneIdConfigured_nonProdEnv_returnsOverrideDate() {
        var cachePort = mock(CachePort.class);
        var configServicePort = mock(ConfigServicePort.class);
        var adapter = adapter("JP", "25/12/2025", "Asia/Hong_Kong",
                Optional.of(cachePort), configServicePort);
        StepVerifier.create(adapter.resolveReferenceDate())
                .assertNext(date -> assertThat(date).isEqualTo(LocalDate.of(2025, 12, 25)))
                .verifyComplete();

        verifyNoInteractions(cachePort, configServicePort);
    }

    @Test
    void overrideDateAndZoneIdConfigured_productionEnv_returnsSystemDateIgnoringOverride() {
        // Even if override-date is fully configured, PROD env must never use it.
        var adapter = adapter("PROD", "25/12/2025", "Asia/Hong_Kong");
        StepVerifier.create(adapter.resolveReferenceDate())
                .assertNext(date -> assertThat(date).isEqualTo(FIXED_DATE))
                .verifyComplete();
    }

    @ParameterizedTest
    @ValueSource(strings = {"PROD", "PRD", "PRODUCTION", "DR",
                             "prod", "pRoD", "dr", "production"})
    void productionLikeEnvVariants_alwaysReturnSystemDate(String accountEnv) {
        var adapter = adapter(accountEnv, "25/12/2025", "Asia/Hong_Kong");
        StepVerifier.create(adapter.resolveReferenceDate())
                .assertNext(date -> assertThat(date).isEqualTo(FIXED_DATE))
                .verifyComplete();
    }

    @Test
    void noOverrideDateConfigured_nonProdEnv_returnsSystemDate() {
        var adapter = adapter("JP", "", "");
        StepVerifier.create(adapter.resolveReferenceDate())
                .assertNext(date -> assertThat(date).isEqualTo(FIXED_DATE))
                .verifyComplete();
    }

    // ── System-date fallback ──────────────────────────────────────────────────

    @Test
    void systemDate_withOverrideZoneId_usesConfiguredZoneAppliedToFixedClock() {
        // 2026-03-15 23:30:00 UTC → Asia/Hong_Kong (UTC+8) → 2026-03-16 07:30:00 → date 2026-03-16
        var lateUtcClock = Clock.fixed(
                LocalDate.of(2026, 3, 15).atTime(23, 30).atZone(UTC).toInstant(), UTC);
        var props = new ReferenceDateProperties();
        props.setAccountEnv("JP");
        props.setOverrideDate("");
        props.setOverrideZoneId("Asia/Hong_Kong");
        var configServicePort = mock(ConfigServicePort.class);
        when(configServicePort.listConfigs(any())).thenReturn(Mono.just(List.of()));
        var adapter = new OrchestratedReferenceDateAdapter(
                props, Optional.empty(), "ngtpa", configServicePort, lateUtcClock);

        StepVerifier.create(adapter.resolveReferenceDate())
                .assertNext(date -> assertThat(date).isEqualTo(LocalDate.of(2026, 3, 16)))
                .verifyComplete();
    }

    @Test
    void systemDate_noOverrideZoneId_usesClockZone() {
        var adapter = adapter("JP", "", "");
        StepVerifier.create(adapter.resolveReferenceDate())
                .assertNext(date -> assertThat(date).isEqualTo(FIXED_DATE))
                .verifyComplete();
    }

    @Test
    void systemDate_overrideZoneIdAlsoAppliedForProductionEnvs() {
        // For production-like envs the override-date is ignored, but override-zone-id
        // is still honoured for the system-date zone.
        // FIXED_CLOCK = 2026-03-15 00:00 UTC. Asia/Tokyo (UTC+9) = 2026-03-15 09:00 → still 2026-03-15.
        var adapter = adapter("PROD", "25/12/2025", "Asia/Tokyo");
        StepVerifier.create(adapter.resolveReferenceDate())
                .assertNext(date -> assertThat(date).isEqualTo(FIXED_DATE))
                .verifyComplete();
    }

    // ── Production-like null / blank env ─────────────────────────────────────

    @Test
    void nullAccountEnv_treatedAsProductionLike_returnsSystemDate() {
        var adapter = adapter(null, "25/12/2025", "Asia/Hong_Kong");
        StepVerifier.create(adapter.resolveReferenceDate())
                .assertNext(date -> assertThat(date).isEqualTo(FIXED_DATE))
                .verifyComplete();
    }

    @Test
    void blankAccountEnv_treatedAsProductionLike_returnsSystemDate() {
        var adapter = adapter("  ", "25/12/2025", "Asia/Hong_Kong");
        StepVerifier.create(adapter.resolveReferenceDate())
                .assertNext(date -> assertThat(date).isEqualTo(FIXED_DATE))
                .verifyComplete();
    }

    // ── Override-date pair validation ─────────────────────────────────────────

    @Test
    void overrideDateWithoutZoneId_nonProdEnv_signalsInvalidContributionRequestException() {
        var adapter = adapter("JP", "25/12/2025", "");
        StepVerifier.create(adapter.resolveReferenceDate())
                .expectErrorSatisfies(ex -> {
                    assertThat(ex).isInstanceOf(InvalidContributionRequestException.class);
                    assertThat(ex.getMessage())
                            .isEqualTo("reference-date.override-date and reference-date.override-zone-id must be provided together");
                })
                .verify();
    }

    @Test
    void overrideDateWithInvalidZoneId_nonProdEnv_signalsInvalidContributionRequestException() {
        var adapter = adapter("sit", "25/12/2025", "NotAZone/Invalid");
        StepVerifier.create(adapter.resolveReferenceDate())
                .expectErrorSatisfies(ex -> {
                    assertThat(ex).isInstanceOf(InvalidContributionRequestException.class);
                    assertThat(ex.getMessage()).isEqualTo("reference-date.override-zone-id is invalid");
                })
                .verify();
    }

    @Test
    void overrideDateWithInvalidFormat_nonProdEnv_signalsInvalidContributionRequestException() {
        var adapter = adapter("uat", "2025-12-25", "Asia/Hong_Kong");
        StepVerifier.create(adapter.resolveReferenceDate())
                .expectErrorSatisfies(ex -> {
                    assertThat(ex).isInstanceOf(InvalidContributionRequestException.class);
                    assertThat(ex.getMessage())
                            .isEqualTo("reference-date.override-date must use dd/MM/yyyy format");
                })
                .verify();
    }

    // ── Redis read-path helper ────────────────────────────────────────────────
    /**
     * Builds an adapter with a specific Redis key prefix for Redis read-path tests.
     */
    private OrchestratedReferenceDateAdapter adapterWithRedis(
            String overrideDate,
            String overrideZoneId,
            String keyPrefix,
            Optional<CachePort> cachePort,
            ConfigServicePort configServicePort) {
        return adapterWithRedis("JP", overrideDate, overrideZoneId, keyPrefix, cachePort, configServicePort);
    }

    private OrchestratedReferenceDateAdapter adapterWithRedis(
            String accountEnv,
            String overrideDate,
            String overrideZoneId,
            String keyPrefix,
            Optional<CachePort> cachePort,
            ConfigServicePort configServicePort) {
        var props = new ReferenceDateProperties();
        props.setAccountEnv(accountEnv);
        props.setOverrideDate(overrideDate);
        props.setOverrideZoneId(overrideZoneId);
        return new OrchestratedReferenceDateAdapter(
                props, cachePort, keyPrefix, configServicePort, FIXED_CLOCK);
    }

    // ── Redis hit ──────────────────────────────────────────────────────────────

    @Test
    void redisCacheHit_parsesDateAndReturnsWithoutCallingConfigService() {
        var cachePort = mock(CachePort.class);
        var configServicePort = mock(ConfigServicePort.class);
        when(cachePort.get("ngtpa:reference-date:JP"))
                .thenReturn(Mono.just(Optional.of("25/12/2025")));

        StepVerifier.create(adapterWithRedis("" , "", "ngtpa",
                Optional.of(cachePort), configServicePort)
                .resolveReferenceDate())
                .assertNext(date -> assertThat(date).isEqualTo(LocalDate.of(2025, 12, 25)))
                .verifyComplete();

        verify(cachePort).get("ngtpa:reference-date:JP");
        verifyNoInteractions(configServicePort);
    }

    @Test
    void redisCacheHit_keyFormat_isPrefix_colon_capability_colon_accountEnv() {
        var cachePort = mock(CachePort.class);
        when(cachePort.get("myprefix:reference-date:HK"))
                .thenReturn(Mono.just(Optional.of("01/06/2026")));

        StepVerifier.create(adapterWithRedis("HK", "", "", "myprefix", Optional.of(cachePort), mock(ConfigServicePort.class)).resolveReferenceDate())
                .assertNext(date -> assertThat(date).isEqualTo(LocalDate.of(2026, 6, 1)))
                .verifyComplete();

        verify(cachePort).get("myprefix:reference-date:HK");
    }

    // ── Redis miss ────────────────────────────────────────────────────────────

    @Test
    void redisCacheMiss_callsConfigServiceAndFallsBackToSystemDate() {
        var cachePort = mock(CachePort.class);
        var configServicePort = mock(ConfigServicePort.class);
        when(cachePort.get("ngtpa:reference-date:JP")).thenReturn(Mono.just(Optional.empty()));
        when(configServicePort.listConfigs(any())).thenReturn(Mono.just(List.of()));

        StepVerifier.create(adapterWithRedis("", "", "ngtpa",
                Optional.of(cachePort), configServicePort)
                .resolveReferenceDate())
                .assertNext(date -> assertThat(date).isEqualTo(FIXED_DATE))
                .verifyComplete();

        verify(cachePort).get("ngtpa:reference-date:JP");
        verify(configServicePort).listConfigs(
                argThat(q -> "reference-date.JP".equals(q.configKey())));
    }

    @Test
    void redisCacheMiss_configServiceHit_returnsConfigServiceDate() {
        var cachePort = mock(CachePort.class);
        var configServicePort = mock(ConfigServicePort.class);
        when(cachePort.get("ngtpa:reference-date:JP")).thenReturn(Mono.just(Optional.empty()));
        when(configServicePort.listConfigs(any())).thenReturn(Mono.just(List.of(
                new ConfigEntry(null, null, null, "reference-date.JP", "01/06/2026"))));

        StepVerifier.create(adapterWithRedis("", "", "ngtpa",
                Optional.of(cachePort), configServicePort)
                .resolveReferenceDate())
                .assertNext(date -> assertThat(date).isEqualTo(LocalDate.of(2026, 6, 1)))
                .verifyComplete();

        verify(cachePort).get("ngtpa:reference-date:JP");
        verify(configServicePort).listConfigs(
                argThat(q -> "reference-date.JP".equals(q.configKey())));
    }

    // ── Redis disabled ────────────────────────────────────────────────────────

    @Test
    void redisCacheDisabled_skipsRedisAndCallsConfigService() {
        var configServicePort = mock(ConfigServicePort.class);
        when(configServicePort.listConfigs(any())).thenReturn(Mono.just(List.of()));

        StepVerifier.create(adapterWithRedis("", "", "ngtpa",
                Optional.empty(), configServicePort)
                .resolveReferenceDate())
                .assertNext(date -> assertThat(date).isEqualTo(FIXED_DATE))
                .verifyComplete();

        verify(configServicePort).listConfigs(
                argThat(q -> "reference-date.JP".equals(q.configKey())));
    }

    @Test
    void redisCacheDisabled_configServiceHit_returnsConfigServiceDate() {
        var configServicePort = mock(ConfigServicePort.class);
        when(configServicePort.listConfigs(any())).thenReturn(Mono.just(List.of(
                new ConfigEntry(null, null, null, "reference-date.JP", "25/12/2025"))));

        StepVerifier.create(adapterWithRedis("", "", "ngtpa",
                Optional.empty(), configServicePort)
                .resolveReferenceDate())
                .assertNext(date -> assertThat(date).isEqualTo(LocalDate.of(2025, 12, 25)))
                .verifyComplete();

        verify(configServicePort).listConfigs(
                argThat(q -> "reference-date.JP".equals(q.configKey())));
    }

    // ── Redis read error ──────────────────────────────────────────────────────

    @Test
    void redisCacheReadError_logsWarningSanitizedAndCallsConfigService() {
        var cachePort = mock(CachePort.class);
        var configServicePort = mock(ConfigServicePort.class);
        when(cachePort.get(any())).thenReturn(Mono.error(
                new CacheException("Cache get operation failed",
                        new RuntimeException("lettuce connection refused redis://secret-host:6379"))));
        when(configServicePort.listConfigs(any())).thenReturn(Mono.just(List.of()));

        var logger = (Logger) LoggerFactory.getLogger(OrchestratedReferenceDateAdapter.class);
        var appender = new ListAppender<ILoggingEvent>();
        appender.start();
        logger.addAppender(appender);
        try {
            StepVerifier.create(adapterWithRedis("", "", "ngtpa",
                    Optional.of(cachePort), configServicePort)
                    .resolveReferenceDate())
                    .assertNext(date -> assertThat(date).isEqualTo(FIXED_DATE))
                    .verifyComplete();

            assertThat(appender.list)
                    .anyMatch(e -> e.getLevel() == Level.WARN
                            && e.getFormattedMessage().contains("reference-date")
                            && !e.getFormattedMessage().contains("secret-host")
                            && !e.getFormattedMessage().contains("6379"));

            verify(configServicePort).listConfigs(any());
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    @Test
    void redisCacheReadError_configServiceHit_returnsConfigServiceDateAndLogsSanitizedWarning() {
        var cachePort = mock(CachePort.class);
        var configServicePort = mock(ConfigServicePort.class);
        when(cachePort.get(any())).thenReturn(Mono.error(
                new CacheException("Cache get operation failed",
                        new RuntimeException("lettuce connection refused redis://secret-host:6379"))));
        when(configServicePort.listConfigs(any())).thenReturn(Mono.just(List.of(
                new ConfigEntry(null, null, null, "reference-date.JP", "01/06/2026"))));

        var logger = (Logger) LoggerFactory.getLogger(OrchestratedReferenceDateAdapter.class);
        var appender = new ListAppender<ILoggingEvent>();
        appender.start();
        logger.addAppender(appender);
        try {
            StepVerifier.create(adapterWithRedis("", "", "ngtpa",
                    Optional.of(cachePort), configServicePort)
                    .resolveReferenceDate())
                    .assertNext(date -> assertThat(date).isEqualTo(LocalDate.of(2026, 6, 1)))
                    .verifyComplete();

            assertThat(appender.list)
                    .anyMatch(e -> e.getLevel() == Level.WARN
                            && e.getFormattedMessage().contains("reference-date")
                            && !e.getFormattedMessage().contains("secret-host")
                            && !e.getFormattedMessage().contains("6379"));

            verify(configServicePort).listConfigs(
                    argThat(q -> "reference-date.JP".equals(q.configKey())));
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    // ── Redis invalid date ────────────────────────────────────────────────────

    @Test
    void redisCacheInvalidDate_logsWarningAndCallsConfigService() {
        var cachePort = mock(CachePort.class);
        var configServicePort = mock(ConfigServicePort.class);
        when(cachePort.get("ngtpa:reference-date:JP"))
                .thenReturn(Mono.just(Optional.of("not-a-valid-date")));
        when(configServicePort.listConfigs(any())).thenReturn(Mono.just(List.of()));

        var logger = (Logger) LoggerFactory.getLogger(OrchestratedReferenceDateAdapter.class);
        var appender = new ListAppender<ILoggingEvent>();
        appender.start();
        logger.addAppender(appender);
        try {
            StepVerifier.create(adapterWithRedis("", "", "ngtpa",
                    Optional.of(cachePort), configServicePort)
                    .resolveReferenceDate())
                    .assertNext(date -> assertThat(date).isEqualTo(FIXED_DATE))
                    .verifyComplete();

            assertThat(appender.list)
                    .anyMatch(e -> e.getLevel() == Level.WARN
                            && e.getFormattedMessage().contains("reference-date"));

            verify(configServicePort).listConfigs(
                    argThat(q -> "reference-date.JP".equals(q.configKey())));
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    @Test
    void redisCacheInvalidDate_configServiceHit_returnsConfigServiceDateAndLogsSanitizedWarning() {
        var cachePort = mock(CachePort.class);
        var configServicePort = mock(ConfigServicePort.class);
        when(cachePort.get("ngtpa:reference-date:JP"))
                .thenReturn(Mono.just(Optional.of("not-a-valid-date")));
        when(configServicePort.listConfigs(any())).thenReturn(Mono.just(List.of(
                new ConfigEntry(null, null, null, "reference-date.JP", "01/06/2026"))));

        var logger = (Logger) LoggerFactory.getLogger(OrchestratedReferenceDateAdapter.class);
        var appender = new ListAppender<ILoggingEvent>();
        appender.start();
        logger.addAppender(appender);
        try {
            StepVerifier.create(adapterWithRedis("", "", "ngtpa",
                    Optional.of(cachePort), configServicePort)
                    .resolveReferenceDate())
                    .assertNext(date -> assertThat(date).isEqualTo(LocalDate.of(2026, 6, 1)))
                    .verifyComplete();

            assertThat(appender.list)
                    .anyMatch(e -> e.getLevel() == Level.WARN
                            && e.getFormattedMessage().contains("reference-date")
                            && !e.getFormattedMessage().contains("not-a-valid-date"));

            verify(configServicePort).listConfigs(
                    argThat(q -> "reference-date.JP".equals(q.configKey())));
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    // ── Config Service read-path helper ────────────────────────────────────────
    /**
     * Builds an adapter configured for Config Service read-path tests.
     * Sets {@code cacheTtlSeconds} on {@link ReferenceDateProperties} to control
     * whether Redis populate is expected after a Config Service hit.
     */
    private OrchestratedReferenceDateAdapter adapterForConfigService(
            Optional<CachePort> cachePort,
            long cacheTtlSeconds,
            ConfigServicePort configServicePort) {
        return adapterForConfigService("JP", cachePort, cacheTtlSeconds, configServicePort);
    }

    private OrchestratedReferenceDateAdapter adapterForConfigService(
            String accountEnv,
            Optional<CachePort> cachePort,
            long cacheTtlSeconds,
            ConfigServicePort configServicePort) {
        var props = new ReferenceDateProperties();
        props.setAccountEnv(accountEnv);
        props.setCacheTtlSeconds(cacheTtlSeconds);
        return new OrchestratedReferenceDateAdapter(
                props, cachePort, "ngtpa", configServicePort, FIXED_CLOCK);
    }

    // ── Config Service hit: date parsing ─────────────────────────────────────

    @Test
    void configServiceHit_parsesDateAndReturnsLocalDate() {
        var configServicePort = mock(ConfigServicePort.class);
        when(configServicePort.listConfigs(any())).thenReturn(Mono.just(List.of(
                new ConfigEntry(null, null, null, "reference-date.JP", "25/12/2025"))));

        StepVerifier.create(adapterForConfigService(Optional.empty(), 0, configServicePort)
                .resolveReferenceDate())
                .assertNext(date -> assertThat(date).isEqualTo(LocalDate.of(2025, 12, 25)))
                .verifyComplete();
    }

    @Test
    void configServiceHit_usesConfigKeyFormat_referenceDate_dot_accountEnv() {
        var configServicePort = mock(ConfigServicePort.class);
        when(configServicePort.listConfigs(argThat(q -> "reference-date.HK".equals(q.configKey()))))
                .thenReturn(Mono.just(List.of(
                        new ConfigEntry(null, null, null, "reference-date.HK", "01/06/2026"))));

        StepVerifier.create(adapterForConfigService("HK", Optional.empty(), 0, configServicePort).resolveReferenceDate())
                .assertNext(date -> assertThat(date).isEqualTo(LocalDate.of(2026, 6, 1)))
                .verifyComplete();

        verify(configServicePort).listConfigs(argThat(q -> "reference-date.HK".equals(q.configKey())));
    }

    // ── Config Service hit: Redis populate ──────────────────────────────────

    @Test
    void configServiceHit_populatesRedisWithKeyAndTtl() {
        var cachePort = mock(CachePort.class);
        var configServicePort = mock(ConfigServicePort.class);
        when(cachePort.get(any())).thenReturn(Mono.just(Optional.empty())); // Redis miss → Config Service
        when(configServicePort.listConfigs(any())).thenReturn(Mono.just(List.of(
                new ConfigEntry(null, null, null, "reference-date.JP", "25/12/2025"))));
        when(cachePort.set(any(), any(), any())).thenReturn(Mono.empty());

        StepVerifier.create(adapterForConfigService(Optional.of(cachePort), 86400, configServicePort)
                .resolveReferenceDate())
                .assertNext(date -> assertThat(date).isEqualTo(LocalDate.of(2025, 12, 25)))
                .verifyComplete();

        verify(cachePort).set(
                argThat(k -> "ngtpa:reference-date:JP".equals(k)),
                argThat(v -> "25/12/2025".equals(v)),
                argThat(d -> d.getSeconds() == 86400));
    }

    @Test
    void configServiceHit_populatesRedisWithCorrectKeyPrefix() {
        var cachePort = mock(CachePort.class);
        var configServicePort = mock(ConfigServicePort.class);
        when(cachePort.get(any())).thenReturn(Mono.just(Optional.empty())); // Redis miss → Config Service
        when(configServicePort.listConfigs(any())).thenReturn(Mono.just(List.of(
                new ConfigEntry(null, null, null, "reference-date.HK", "15/03/2026"))));
        when(cachePort.set(any(), any(), any())).thenReturn(Mono.empty());

        var props = new ReferenceDateProperties();
        props.setAccountEnv("HK");
        props.setCacheTtlSeconds(3600);
        var adapter = new OrchestratedReferenceDateAdapter(
                props, Optional.of(cachePort), "mypfx", configServicePort, FIXED_CLOCK);

        StepVerifier.create(adapter.resolveReferenceDate())
                .assertNext(date -> assertThat(date).isEqualTo(LocalDate.of(2026, 3, 15)))
                .verifyComplete();

        verify(cachePort).set(
                argThat(k -> "mypfx:reference-date:HK".equals(k)),
                argThat(v -> "15/03/2026".equals(v)),
                any());
    }

    @Test
    void configServiceHit_noTtlConfigured_skipsRedisPopulate() {
        var cachePort = mock(CachePort.class);
        var configServicePort = mock(ConfigServicePort.class);
        when(cachePort.get(any())).thenReturn(Mono.just(Optional.empty())); // Redis miss → Config Service
        when(configServicePort.listConfigs(any())).thenReturn(Mono.just(List.of(
                new ConfigEntry(null, null, null, "reference-date.JP", "25/12/2025"))));

        StepVerifier.create(adapterForConfigService(Optional.of(cachePort), 0, configServicePort)
                .resolveReferenceDate())
                .assertNext(date -> assertThat(date).isEqualTo(LocalDate.of(2025, 12, 25)))
                .verifyComplete();

        verify(cachePort, never()).set(any(), any(), any());
    }

    @Test
    void configServiceHit_redisDisabled_skipsRedisPopulate() {
        var configServicePort = mock(ConfigServicePort.class);
        when(configServicePort.listConfigs(any())).thenReturn(Mono.just(List.of(
                new ConfigEntry(null, null, null, "reference-date.JP", "25/12/2025"))));

        StepVerifier.create(adapterForConfigService(Optional.empty(), 86400, configServicePort)
                .resolveReferenceDate())
                .assertNext(date -> assertThat(date).isEqualTo(LocalDate.of(2025, 12, 25)))
                .verifyComplete();
    }

    @Test
    void configServiceHit_redisPopulateError_logsWarnAndStillReturnsDate() {
        var cachePort = mock(CachePort.class);
        var configServicePort = mock(ConfigServicePort.class);
        when(cachePort.get(any())).thenReturn(Mono.just(Optional.empty())); // Redis miss → Config Service
        when(configServicePort.listConfigs(any())).thenReturn(Mono.just(List.of(
                new ConfigEntry(null, null, null, "reference-date.JP", "25/12/2025"))));
        when(cachePort.set(any(), any(), any())).thenReturn(Mono.error(
                new CacheException("Cache set operation failed",
                        new RuntimeException("lettuce write failed redis://secret-host:6379"))));

        var logger = (Logger) LoggerFactory.getLogger(OrchestratedReferenceDateAdapter.class);
        var appender = new ListAppender<ILoggingEvent>();
        appender.start();
        logger.addAppender(appender);
        try {
            StepVerifier.create(adapterForConfigService(Optional.of(cachePort), 86400, configServicePort)
                    .resolveReferenceDate())
                    .assertNext(date -> assertThat(date).isEqualTo(LocalDate.of(2025, 12, 25)))
                    .verifyComplete();

            assertThat(appender.list)
                    .anyMatch(e -> e.getLevel() == Level.WARN
                            && e.getFormattedMessage().contains("reference-date")
                            && !e.getFormattedMessage().contains("secret-host")
                            && !e.getFormattedMessage().contains("6379"));
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    // ── Config Service miss / invalid value ───────────────────────────────

    @Test
    void configServiceMiss_fallsBackToSystemDate() {
        var configServicePort = mock(ConfigServicePort.class);
        when(configServicePort.listConfigs(any())).thenReturn(Mono.just(List.of()));

        StepVerifier.create(adapterForConfigService(Optional.empty(), 0, configServicePort)
                .resolveReferenceDate())
                .assertNext(date -> assertThat(date).isEqualTo(FIXED_DATE))
                .verifyComplete();
    }

    @Test
    void configServiceHit_invalidDateValue_logsWarnAndFallsBackToSystemDate() {
        var configServicePort = mock(ConfigServicePort.class);
        when(configServicePort.listConfigs(any())).thenReturn(Mono.just(List.of(
                new ConfigEntry(null, null, null, "reference-date.JP", "not-a-date"))));

        var logger = (Logger) LoggerFactory.getLogger(OrchestratedReferenceDateAdapter.class);
        var appender = new ListAppender<ILoggingEvent>();
        appender.start();
        logger.addAppender(appender);
        try {
            StepVerifier.create(adapterForConfigService(Optional.empty(), 0, configServicePort)
                    .resolveReferenceDate())
                    .assertNext(date -> assertThat(date).isEqualTo(FIXED_DATE))
                    .verifyComplete();

            assertThat(appender.list)
                    .anyMatch(e -> e.getLevel() == Level.WARN
                            && e.getFormattedMessage().contains("reference-date"));
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    // ── Config Service error ─────────────────────────────────────────────────────

    @Test
    void configServiceError_logsWarnAndFallsBackToSystemDate() {
        var configServicePort = mock(ConfigServicePort.class);
        when(configServicePort.listConfigs(any())).thenReturn(
                Mono.error(new RuntimeException("Config Service connection timeout")));

        var logger = (Logger) LoggerFactory.getLogger(OrchestratedReferenceDateAdapter.class);
        var appender = new ListAppender<ILoggingEvent>();
        appender.start();
        logger.addAppender(appender);
        try {
            StepVerifier.create(adapterForConfigService(Optional.empty(), 0, configServicePort)
                    .resolveReferenceDate())
                    .assertNext(date -> assertThat(date).isEqualTo(FIXED_DATE))
                    .verifyComplete();

            assertThat(appender.list)
                    .anyMatch(e -> e.getLevel() == Level.WARN
                            && e.getFormattedMessage().contains("reference-date")
                            && !e.getFormattedMessage().contains("connection timeout"));
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    // ── Config Service timeout ────────────────────────────────────────────────

    @Test
    void configServiceTimeout_fallsBackToSystemDateWithSanitizedWarn() {
        var configServicePort = mock(ConfigServicePort.class);
        when(configServicePort.listConfigs(any())).thenReturn(
                Mono.error(new TimeoutException("socket read timeout after 5000ms")));

        var logger = (Logger) LoggerFactory.getLogger(OrchestratedReferenceDateAdapter.class);
        var appender = new ListAppender<ILoggingEvent>();
        appender.start();
        logger.addAppender(appender);
        try {
            StepVerifier.create(adapterForConfigService("HK", Optional.empty(), 0, configServicePort).resolveReferenceDate())
                    .assertNext(date -> assertThat(date).isEqualTo(FIXED_DATE))
                    .verifyComplete();

            assertThat(appender.list)
                    .anyMatch(e -> e.getLevel() == Level.WARN
                            && e.getFormattedMessage().contains("reference-date")
                            && e.getFormattedMessage().contains("HK")
                            && !e.getFormattedMessage().contains("socket read timeout after 5000ms"));
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    // ── Config Service observability: log context ─────────────────────────────

    @Test
    void configServiceError_warnLogIncludesAccountEnv() {
        var configServicePort = mock(ConfigServicePort.class);
        when(configServicePort.listConfigs(any())).thenReturn(
                Mono.error(new RuntimeException("connection refused")));

        var logger = (Logger) LoggerFactory.getLogger(OrchestratedReferenceDateAdapter.class);
        var appender = new ListAppender<ILoggingEvent>();
        appender.start();
        logger.addAppender(appender);
        try {
            StepVerifier.create(adapterForConfigService(Optional.empty(), 0, configServicePort)
                    .resolveReferenceDate())
                    .assertNext(date -> assertThat(date).isEqualTo(FIXED_DATE))
                    .verifyComplete();

            assertThat(appender.list)
                    .anyMatch(e -> e.getLevel() == Level.WARN
                            && e.getFormattedMessage().contains("JP")
                            && !e.getFormattedMessage().contains("connection refused"));
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    @Test
    void configServiceInvalidDate_warnLogIncludesAccountEnv() {
        var configServicePort = mock(ConfigServicePort.class);
        when(configServicePort.listConfigs(any())).thenReturn(Mono.just(List.of(
                new ConfigEntry(null, null, null, "reference-date.JP", "not-a-valid-date"))));

        var logger = (Logger) LoggerFactory.getLogger(OrchestratedReferenceDateAdapter.class);
        var appender = new ListAppender<ILoggingEvent>();
        appender.start();
        logger.addAppender(appender);
        try {
            StepVerifier.create(adapterForConfigService(Optional.empty(), 0, configServicePort)
                    .resolveReferenceDate())
                    .assertNext(date -> assertThat(date).isEqualTo(FIXED_DATE))
                    .verifyComplete();

            assertThat(appender.list)
                    .anyMatch(e -> e.getLevel() == Level.WARN
                            && e.getFormattedMessage().contains("JP"));
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }
}
