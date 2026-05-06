package com.bct.ngtpa.apiservice.adapter.out.configserver;

import com.bct.ngtpa.apiservice.application.exception.InvalidContributionRequestException;
import com.bct.ngtpa.apiservice.config.ReferenceDateProperties;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ConfigBackedReferenceDateAdapterTest {

    @Test
    void ignoresOverrideForProductionEnv() {
        var properties = new ReferenceDateProperties();
        properties.setZoneId("Asia/Hong_Kong");
        properties.setNonProdOverride("31/03/2026");
        var adapter = new ConfigBackedReferenceDateAdapter(properties);

        var expected = LocalDate.now(ZoneId.of("Asia/Hong_Kong"));

        assertEquals(expected, adapter.resolveReferenceDate("pRoD").block());
    }

    @Test
    void usesConfiguredOverrideForNonProductionEnv() {
        var properties = new ReferenceDateProperties();
        properties.setNonProdOverride("31/03/2026");
        var adapter = new ConfigBackedReferenceDateAdapter(properties);

        assertEquals(LocalDate.of(2026, 3, 31), adapter.resolveReferenceDate("JP").block());
    }

    @Test
    void returnsTodayWhenNonProductionOverrideIsBlank() {
        var properties = new ReferenceDateProperties();
        properties.setZoneId("Asia/Hong_Kong");
        properties.setNonProdOverride(" ");
        var adapter = new ConfigBackedReferenceDateAdapter(properties);

        var expected = LocalDate.now(ZoneId.of("Asia/Hong_Kong"));

        assertEquals(expected, adapter.resolveReferenceDate("JP").block());
    }

    @Test
    void treatsBlankEnvAsProductionSafe() {
        var properties = new ReferenceDateProperties();
        properties.setZoneId("Asia/Hong_Kong");
        properties.setNonProdOverride("31/03/2026");
        var adapter = new ConfigBackedReferenceDateAdapter(properties);

        var expected = LocalDate.now(ZoneId.of("Asia/Hong_Kong"));

        assertEquals(expected, adapter.resolveReferenceDate(" ").block());
    }

    @Test
    void rejectsInvalidOverrideFormat() {
        var properties = new ReferenceDateProperties();
        properties.setNonProdOverride("2026-03-31");
        var adapter = new ConfigBackedReferenceDateAdapter(properties);

        var ex = assertThrows(InvalidContributionRequestException.class,
                () -> adapter.resolveReferenceDate("JP").block());

        assertEquals("reference-date.non-prod-override must use dd/MM/yyyy format", ex.getMessage());
    }

    @Test
    void rejectsInvalidZoneId() {
        var properties = new ReferenceDateProperties();
        properties.setZoneId("Mars/Olympus");
        var adapter = new ConfigBackedReferenceDateAdapter(properties);

        var ex = assertThrows(InvalidContributionRequestException.class,
                () -> adapter.resolveReferenceDate("JP").block());

        assertEquals("reference-date.zone-id is invalid", ex.getMessage());
    }
}