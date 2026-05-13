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
    void usesServerDateForProductionLikeDeployment() {
        var resolved = resolver.resolve("prod", "31/03/2026", "Asia/Tokyo");

        assertEquals(LocalDate.of(2026, 5, 7), resolved);
    }

    @Test
    void treatsUppercaseProdAsProductionLike() {
        assertEquals(LocalDate.of(2026, 5, 7), resolver.resolve("PROD", "31/03/2026", "Asia/Hong_Kong"));
    }

    @Test
    void treatsPrdAsProductionLike() {
        assertEquals(LocalDate.of(2026, 5, 7), resolver.resolve("PRD", "31/03/2026", "Asia/Hong_Kong"));
    }

    @Test
    void treatsProductionAsProductionLike() {
        assertEquals(LocalDate.of(2026, 5, 7), resolver.resolve("PRODUCTION", "31/03/2026", "Asia/Hong_Kong"));
    }

    @Test
    void treatsDrAsProductionLike() {
        assertEquals(LocalDate.of(2026, 5, 7), resolver.resolve("DR", "31/03/2026", "Asia/Hong_Kong"));
    }

    @Test
    void treatsNullDeploymentEnvAsProductionLike() {
        assertEquals(LocalDate.of(2026, 5, 7), resolver.resolve(null, "31/03/2026", "Asia/Hong_Kong"));
    }

    @Test
    void treatsBlankDeploymentEnvAsProductionLike() {
        assertEquals(LocalDate.of(2026, 5, 7), resolver.resolve("   ", "31/03/2026", "Asia/Hong_Kong"));
    }

    @Test
    void usesServerDateWhenNonProductionOverridePairIsAbsent() {
        var resolved = resolver.resolve("sit", "", "");

        assertEquals(LocalDate.of(2026, 5, 7), resolved);
    }

    @Test
    void usesConfiguredOverridePairForNonProductionLikeDeployment() {
        var resolved = resolver.resolve("sit", "31/03/2026", "Asia/Hong_Kong");

        assertEquals(LocalDate.of(2026, 3, 31), resolved);
    }

    @Test
    void rejectsPartialOverridePair() {
        var ex = assertThrows(InvalidContributionRequestException.class,
                () -> resolver.resolve("sit", "31/03/2026", ""));

        assertEquals("reference-date.override-date and reference-date.override-zone-id must be provided together",
                ex.getMessage());
    }

    @Test
    void rejectsInvalidOverrideDateFormat() {
        var ex = assertThrows(InvalidContributionRequestException.class,
                () -> resolver.resolve("sit", "2026-03-31", "Asia/Hong_Kong"));

        assertEquals("reference-date.override-date must use dd/MM/yyyy format", ex.getMessage());
    }

    @Test
    void rejectsInvalidOverrideZoneId() {
        var ex = assertThrows(InvalidContributionRequestException.class,
                () -> resolver.resolve("sit", "31/03/2026", "Mars/Olympus"));

        assertEquals("reference-date.override-zone-id is invalid", ex.getMessage());
    }
}