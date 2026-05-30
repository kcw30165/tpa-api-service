package com.bct.ngtpa.apiservice.adapter.out.configserver;

import com.bct.ngtpa.apiservice.application.exception.InvalidContributionRequestException;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ReferenceDateResolverTest {

    private final ReferenceDateResolver resolver = new ReferenceDateResolver(
            Clock.fixed(Instant.parse("2026-05-07T00:00:00Z"), ZoneId.of("UTC")));

    @Test
    void usesServerDateWhenDeploymentEnvIsProductionLikeEvenIfAccountEnvIsBusinessOnly() {
        var resolved = resolver.resolve("JP", "prod", "31/03/2026", "Asia/Tokyo");

        assertEquals(LocalDate.of(2026, 5, 7), resolved);
    }

    @Test
    void treatsDrDeploymentEnvAsProductionLikeEvenWhenAccountEnvNeedsLookup() {
        assertEquals(LocalDate.of(2026, 5, 7), resolver.resolve("JP", "DR", "31/03/2026", "Asia/Hong_Kong"));
    }

    @Test
    void usesConfiguredOverridePairForNonProductionDeploymentUsingAccountEnvLookup() {
        var resolved = resolver.resolve("JP", "sit", "31/03/2026", "Asia/Hong_Kong");

        assertEquals(LocalDate.of(2026, 3, 31), resolved);
    }

    @Test
    void usesConfiguredOverridePairWhenAccountEnvLooksProductionLikeButDeploymentEnvIsNot() {
        var resolved = resolver.resolve("PROD", "sit", "31/03/2026", "Asia/Hong_Kong");

        assertEquals(LocalDate.of(2026, 3, 31), resolved);
    }

    @Test
    void usesServerDateWhenDeploymentEnvIsBlankForFailSafeBehavior() {
        assertEquals(LocalDate.of(2026, 5, 7), resolver.resolve("JP", "   ", "31/03/2026", "Asia/Hong_Kong"));
    }

    @Test
    void usesServerDateWhenNonProductionOverridePairIsAbsent() {
        var resolved = resolver.resolve("JP", "sit", "", "");

        assertEquals(LocalDate.of(2026, 5, 7), resolved);
    }

    @Test
    void rejectsPartialOverridePair() {
        var ex = assertThrows(InvalidContributionRequestException.class,
                () -> resolver.resolve("JP", "sit", "31/03/2026", ""));

        assertEquals("reference-date.override-date and reference-date.override-zone-id must be provided together",
                ex.getMessage());
    }

    @Test
    void rejectsInvalidOverrideDateFormat() {
        var ex = assertThrows(InvalidContributionRequestException.class,
                () -> resolver.resolve("JP", "sit", "2026-03-31", "Asia/Hong_Kong"));

        assertEquals("reference-date.override-date must use dd/MM/yyyy format", ex.getMessage());
    }

    @Test
    void rejectsInvalidOverrideZoneId() {
        var ex = assertThrows(InvalidContributionRequestException.class,
                () -> resolver.resolve("JP", "sit", "31/03/2026", "Mars/Olympus"));

        assertEquals("reference-date.override-zone-id is invalid", ex.getMessage());
    }
}