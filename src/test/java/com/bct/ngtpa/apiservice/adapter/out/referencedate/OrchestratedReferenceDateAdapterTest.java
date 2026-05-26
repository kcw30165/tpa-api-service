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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

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

    private OrchestratedReferenceDateAdapter adapter(String overrideDate, String overrideZoneId) {
        return adapter(overrideDate, overrideZoneId, Optional.empty(), mock(ConfigServicePort.class));
    }

    private OrchestratedReferenceDateAdapter adapter(
            String overrideDate,
            String overrideZoneId,
            Optional<CachePort> cachePort,
            ConfigServicePort configServicePort) {
        var props = new ReferenceDateProperties();
        props.setOverrideDate(overrideDate);
        props.setOverrideZoneId(overrideZoneId);
        return new OrchestratedReferenceDateAdapter(props, cachePort, configServicePort, FIXED_CLOCK);
    }

    // ── Override-date short-circuit ───────────────────────────────────────────

    @Test
    void overrideDateAndZoneIdConfigured_nonProdEnv_returnsOverrideDate() {
        var cachePort = mock(CachePort.class);
        var configServicePort = mock(ConfigServicePort.class);
        var adapter = adapter("25/12/2025", "Asia/Hong_Kong",
                Optional.of(cachePort), configServicePort);

        StepVerifier.create(adapter.resolveReferenceDate("JP"))
                .assertNext(date -> assertThat(date).isEqualTo(LocalDate.of(2025, 12, 25)))
                .verifyComplete();

        verifyNoInteractions(cachePort, configServicePort);
    }

    @Test
    void overrideDateAndZoneIdConfigured_productionEnv_returnsSystemDateIgnoringOverride() {
        // Even if override-date is fully configured, PROD env must never use it.
        var adapter = adapter("25/12/2025", "Asia/Hong_Kong");

        StepVerifier.create(adapter.resolveReferenceDate("PROD"))
                .assertNext(date -> assertThat(date).isEqualTo(FIXED_DATE))
                .verifyComplete();
    }

    @ParameterizedTest
    @ValueSource(strings = {"PROD", "PRD", "PRODUCTION", "DR",
                             "prod", "pRoD", "dr", "production"})
    void productionLikeEnvVariants_alwaysReturnSystemDate(String accountEnv) {
        var adapter = adapter("25/12/2025", "Asia/Hong_Kong");

        StepVerifier.create(adapter.resolveReferenceDate(accountEnv))
                .assertNext(date -> assertThat(date).isEqualTo(FIXED_DATE))
                .verifyComplete();
    }

    @Test
    void noOverrideDateConfigured_nonProdEnv_returnsSystemDate() {
        var adapter = adapter("", "");

        StepVerifier.create(adapter.resolveReferenceDate("JP"))
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
        props.setOverrideDate("");
        props.setOverrideZoneId("Asia/Hong_Kong");
        var adapter = new OrchestratedReferenceDateAdapter(
                props, Optional.empty(), mock(ConfigServicePort.class), lateUtcClock);

        StepVerifier.create(adapter.resolveReferenceDate("JP"))
                .assertNext(date -> assertThat(date).isEqualTo(LocalDate.of(2026, 3, 16)))
                .verifyComplete();
    }

    @Test
    void systemDate_noOverrideZoneId_usesClockZone() {
        var adapter = adapter("", "");

        StepVerifier.create(adapter.resolveReferenceDate("JP"))
                .assertNext(date -> assertThat(date).isEqualTo(FIXED_DATE))
                .verifyComplete();
    }

    @Test
    void systemDate_overrideZoneIdAlsoAppliedForProductionEnvs() {
        // For production-like envs the override-date is ignored, but override-zone-id
        // is still honoured for the system-date zone.
        // FIXED_CLOCK = 2026-03-15 00:00 UTC. Asia/Tokyo (UTC+9) = 2026-03-15 09:00 → still 2026-03-15.
        var adapter = adapter("25/12/2025", "Asia/Tokyo");

        StepVerifier.create(adapter.resolveReferenceDate("PROD"))
                .assertNext(date -> assertThat(date).isEqualTo(FIXED_DATE))
                .verifyComplete();
    }

    // ── Production-like null / blank env ─────────────────────────────────────

    @Test
    void nullAccountEnv_treatedAsProductionLike_returnsSystemDate() {
        var adapter = adapter("25/12/2025", "Asia/Hong_Kong");

        StepVerifier.create(adapter.resolveReferenceDate(null))
                .assertNext(date -> assertThat(date).isEqualTo(FIXED_DATE))
                .verifyComplete();
    }

    @Test
    void blankAccountEnv_treatedAsProductionLike_returnsSystemDate() {
        var adapter = adapter("25/12/2025", "Asia/Hong_Kong");

        StepVerifier.create(adapter.resolveReferenceDate("  "))
                .assertNext(date -> assertThat(date).isEqualTo(FIXED_DATE))
                .verifyComplete();
    }

    // ── Override-date pair validation ─────────────────────────────────────────

    @Test
    void overrideDateWithoutZoneId_nonProdEnv_signalsInvalidContributionRequestException() {
        var adapter = adapter("25/12/2025", "");

        StepVerifier.create(adapter.resolveReferenceDate("JP"))
                .expectErrorSatisfies(ex -> {
                    assertThat(ex).isInstanceOf(InvalidContributionRequestException.class);
                    assertThat(ex.getMessage())
                            .isEqualTo("reference-date.override-date and reference-date.override-zone-id must be provided together");
                })
                .verify();
    }

    @Test
    void overrideDateWithInvalidZoneId_nonProdEnv_signalsInvalidContributionRequestException() {
        var adapter = adapter("25/12/2025", "NotAZone/Invalid");

        StepVerifier.create(adapter.resolveReferenceDate("sit"))
                .expectErrorSatisfies(ex -> {
                    assertThat(ex).isInstanceOf(InvalidContributionRequestException.class);
                    assertThat(ex.getMessage()).isEqualTo("reference-date.override-zone-id is invalid");
                })
                .verify();
    }

    @Test
    void overrideDateWithInvalidFormat_nonProdEnv_signalsInvalidContributionRequestException() {
        var adapter = adapter("2025-12-25", "Asia/Hong_Kong");

        StepVerifier.create(adapter.resolveReferenceDate("uat"))
                .expectErrorSatisfies(ex -> {
                    assertThat(ex).isInstanceOf(InvalidContributionRequestException.class);
                    assertThat(ex.getMessage())
                            .isEqualTo("reference-date.override-date must use dd/MM/yyyy format");
                })
                .verify();
    }
}
