package com.bct.ngtpa.apiservice.adapter.out.configserver;

import com.bct.ngtpa.apiservice.application.exception.InvalidContributionRequestException;
import com.bct.ngtpa.apiservice.config.ReferenceDateProperties;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ConfigBackedReferenceDateAdapterTest {

    private final ReferenceDateResolver referenceDateResolver = new ReferenceDateResolver();

    @Test
    void ignoresOverridePairForProductionLikeEnv() {
        var properties = new ReferenceDateProperties();
        properties.setDeploymentEnv("pRoD");
        properties.setOverrideDate("31/03/2026");
        properties.setOverrideZoneId("Asia/Tokyo");
        var adapter = new ConfigBackedReferenceDateAdapter(properties, referenceDateResolver);

        var expected = LocalDate.now(java.time.ZoneId.systemDefault());

        assertEquals(expected, adapter.resolveReferenceDate().block());
    }

    @Test
    void treatsDrAsProductionLikeEnv() {
        var properties = new ReferenceDateProperties();
        properties.setDeploymentEnv("dr");
        properties.setOverrideDate("31/03/2026");
        properties.setOverrideZoneId("Asia/Tokyo");
        var adapter = new ConfigBackedReferenceDateAdapter(properties, referenceDateResolver);

        var expected = LocalDate.now(java.time.ZoneId.systemDefault());

        assertEquals(expected, adapter.resolveReferenceDate().block());
    }

    @Test
    void usesConfiguredOverridePairForNonProductionLikeEnv() {
        var properties = new ReferenceDateProperties();
        properties.setDeploymentEnv("sit");
        properties.setOverrideDate("31/03/2026");
        properties.setOverrideZoneId("Asia/Hong_Kong");
        var adapter = new ConfigBackedReferenceDateAdapter(properties, referenceDateResolver);

        assertEquals(LocalDate.of(2026, 3, 31), adapter.resolveReferenceDate().block());
    }

    @Test
    void returnsServerDateWhenNonProductionOverridePairIsBlank() {
        var properties = new ReferenceDateProperties();
        properties.setDeploymentEnv("uat");
        var adapter = new ConfigBackedReferenceDateAdapter(properties, referenceDateResolver);

        var expected = LocalDate.now(java.time.ZoneId.systemDefault());

        assertEquals(expected, adapter.resolveReferenceDate().block());
    }

    @Test
    void treatsBlankDeploymentEnvAsProductionSafe() {
        var properties = new ReferenceDateProperties();
        properties.setOverrideDate("31/03/2026");
        properties.setOverrideZoneId("Asia/Hong_Kong");
        var adapter = new ConfigBackedReferenceDateAdapter(properties, referenceDateResolver);

        var expected = LocalDate.now(java.time.ZoneId.systemDefault());

        assertEquals(expected, adapter.resolveReferenceDate().block());
    }

    @Test
    void rejectsInvalidOverrideDateFormat() {
        var properties = new ReferenceDateProperties();
        properties.setDeploymentEnv("dev");
        properties.setOverrideDate("2026-03-31");
        properties.setOverrideZoneId("Asia/Hong_Kong");
        var adapter = new ConfigBackedReferenceDateAdapter(properties, referenceDateResolver);

        var ex = assertThrows(InvalidContributionRequestException.class,
            () -> adapter.resolveReferenceDate().block());

        assertEquals("reference-date.override-date must use dd/MM/yyyy format", ex.getMessage());
    }

    @Test
    void rejectsInvalidOverrideZoneId() {
        var properties = new ReferenceDateProperties();
        properties.setDeploymentEnv("sit");
        properties.setOverrideDate("31/03/2026");
        properties.setOverrideZoneId("Mars/Olympus");
        var adapter = new ConfigBackedReferenceDateAdapter(properties, referenceDateResolver);

        var ex = assertThrows(InvalidContributionRequestException.class,
            () -> adapter.resolveReferenceDate().block());

        assertEquals("reference-date.override-zone-id is invalid", ex.getMessage());
    }

    @Test
    void rejectsPartialOverridePair() {
        var properties = new ReferenceDateProperties();
        properties.setDeploymentEnv("sit");
        properties.setOverrideDate("31/03/2026");
        var adapter = new ConfigBackedReferenceDateAdapter(properties, referenceDateResolver);

        var ex = assertThrows(InvalidContributionRequestException.class,
            () -> adapter.resolveReferenceDate().block());

        assertEquals("reference-date.override-date and reference-date.override-zone-id must be provided together",
                ex.getMessage());
    }
}