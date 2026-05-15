package com.bct.ngtpa.apiservice.adapter.out.configserver;

import com.bct.ngtpa.apiservice.application.exception.InvalidContributionRequestException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ConfigBackedReferenceDateAdapterTest {

    private final ReferenceDateResolver referenceDateResolver = new ReferenceDateResolver(
            Clock.fixed(Instant.parse("2026-05-07T00:00:00Z"), ZoneId.of("UTC")));

    @Test
    void ignoresOverridePairForProductionLikeDeploymentProfile() {
        var properties = new ReferenceDateProperties();
        properties.setAccountEnv("JP");
        properties.setOverrideDate("31/03/2026");
        properties.setOverrideZoneId("Asia/Tokyo");
        var adapter = new ConfigBackedReferenceDateAdapter(
                properties,
                referenceDateResolver,
                environment("prod"));

        assertEquals(LocalDate.of(2026, 5, 7), adapter.resolveReferenceDate().block());
    }

    @Test
    void treatsDrDeploymentProfileAsProductionLike() {
        var properties = new ReferenceDateProperties();
        properties.setAccountEnv("JP");
        properties.setOverrideDate("31/03/2026");
        properties.setOverrideZoneId("Asia/Tokyo");
        var adapter = new ConfigBackedReferenceDateAdapter(
                properties,
                referenceDateResolver,
                environment("dr"));

        assertEquals(LocalDate.of(2026, 5, 7), adapter.resolveReferenceDate().block());
    }

    @Test
    void usesConfiguredOverridePairForNonProductionDeploymentProfile() {
        var properties = new ReferenceDateProperties();
        properties.setAccountEnv("JP");
        properties.setOverrideDate("31/03/2026");
        properties.setOverrideZoneId("Asia/Hong_Kong");
        var adapter = new ConfigBackedReferenceDateAdapter(
                properties,
                referenceDateResolver,
                environment("sit"));

        assertEquals(LocalDate.of(2026, 3, 31), adapter.resolveReferenceDate().block());
    }

    @Test
    void usesConfiguredOverridePairWhenAccountEnvLooksProductionLikeButDeploymentProfileIsNot() {
        var properties = new ReferenceDateProperties();
        properties.setAccountEnv("PROD");
        properties.setOverrideDate("31/03/2026");
        properties.setOverrideZoneId("Asia/Hong_Kong");
        var adapter = new ConfigBackedReferenceDateAdapter(
                properties,
                referenceDateResolver,
                environment("sit"));

        assertEquals(LocalDate.of(2026, 3, 31), adapter.resolveReferenceDate("sit").block());
    }

    @Test
    void returnsServerDateWhenNonProductionOverridePairIsBlank() {
        var properties = new ReferenceDateProperties();
        properties.setAccountEnv("JP");
        var adapter = new ConfigBackedReferenceDateAdapter(
                properties,
                referenceDateResolver,
                environment("uat"));

        assertEquals(LocalDate.of(2026, 5, 7), adapter.resolveReferenceDate().block());
    }

    @Test
    void treatsBlankDeploymentProfileAsProductionSafe() {
        var properties = new ReferenceDateProperties();
        properties.setAccountEnv("JP");
        properties.setOverrideDate("31/03/2026");
        properties.setOverrideZoneId("Asia/Hong_Kong");
        var adapter = new ConfigBackedReferenceDateAdapter(properties, referenceDateResolver, new MockEnvironment());

        assertEquals(LocalDate.of(2026, 5, 7), adapter.resolveReferenceDate().block());
    }

    @Test
    void rejectsInvalidOverrideDateFormat() {
        var properties = new ReferenceDateProperties();
        properties.setAccountEnv("JP");
        properties.setOverrideDate("2026-03-31");
        properties.setOverrideZoneId("Asia/Hong_Kong");
        var adapter = new ConfigBackedReferenceDateAdapter(
                properties,
                referenceDateResolver,
                environment("dev"));

        var ex = assertThrows(InvalidContributionRequestException.class,
                () -> adapter.resolveReferenceDate().block());

        assertEquals("reference-date.override-date must use dd/MM/yyyy format", ex.getMessage());
    }

    @Test
    void rejectsInvalidOverrideZoneId() {
        var properties = new ReferenceDateProperties();
        properties.setAccountEnv("JP");
        properties.setOverrideDate("31/03/2026");
        properties.setOverrideZoneId("Mars/Olympus");
        var adapter = new ConfigBackedReferenceDateAdapter(
                properties,
                referenceDateResolver,
                environment("sit"));

        var ex = assertThrows(InvalidContributionRequestException.class,
                () -> adapter.resolveReferenceDate().block());

        assertEquals("reference-date.override-zone-id is invalid", ex.getMessage());
    }

    @Test
    void rejectsPartialOverridePair() {
        var properties = new ReferenceDateProperties();
        properties.setAccountEnv("JP");
        properties.setOverrideDate("31/03/2026");
        var adapter = new ConfigBackedReferenceDateAdapter(
                properties,
                referenceDateResolver,
                environment("sit"));

        var ex = assertThrows(InvalidContributionRequestException.class,
                () -> adapter.resolveReferenceDate().block());

        assertEquals("reference-date.override-date and reference-date.override-zone-id must be provided together",
                ex.getMessage());
    }

        private static MockEnvironment environment(String... activeProfiles) {
                var environment = new MockEnvironment();
                environment.setActiveProfiles(activeProfiles);
                return environment;
        }
}