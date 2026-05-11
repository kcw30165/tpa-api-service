package com.bct.ngtpa.apiservice.adapter.out.config;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ConfigBackedDateDisplayAdapterTest {

    private static ConfigBackedDateDisplayAdapter adapterWith(Map<String, Map<String, String>> data) {
        var props = new DateFormatProperties();
        props.putAll(data);
        return new ConfigBackedDateDisplayAdapter(props);
    }

    @Test
    void formatsDateUsingDefaultEnPattern() {
        var adapter = adapterWith(Map.of("en", Map.of("default", "dd/MM/yyyy")));

        var result = adapter.formatDate(LocalDate.of(2026, 3, 1), "en", "JP", "", "");
        assertEquals("01/03/2026", result);
    }

    @Test
    void prefersEnvSpecificPatternOverDefault() {
        Map<String, String> enFormats = new LinkedHashMap<>();
        enFormats.put("default", "dd/MM/yyyy");
        enFormats.put("JP", "MM/dd/yyyy");
        var adapter = adapterWith(Map.of("en", enFormats));

        var result = adapter.formatDate(LocalDate.of(2026, 3, 1), "en", "JP", "", "");
        assertEquals("03/01/2026", result);
    }

    @Test
    void fallsBackToIsoWhenNoPatternConfigured() {
        var adapter = adapterWith(Map.of());

        var result = adapter.formatDate(LocalDate.of(2026, 3, 1), "en", "JP", "", "");
        assertEquals("2026-03-01", result);
    }

    @Test
    void fallsBackToDefaultWhenEnvNotFound() {
        var adapter = adapterWith(Map.of("en", Map.of("default", "dd/MM/yyyy")));

        var result = adapter.formatDate(LocalDate.of(2026, 3, 1), "en", "UNKNOWN_ENV", "", "");
        assertEquals("01/03/2026", result);
    }

    @Test
    void usesEnLocaleAsFallbackWhenLangNotFound() {
        var adapter = adapterWith(Map.of("en", Map.of("default", "dd/MM/yyyy")));

        // zh_HK not configured, falls back to en
        var result = adapter.formatDate(LocalDate.of(2026, 3, 1), "zh_HK", "JP", "", "");
        assertEquals("01/03/2026", result);
    }

    @Test
    void returnsEmptyStringForNullDate() {
        var adapter = adapterWith(Map.of("en", Map.of("default", "dd/MM/yyyy")));
        assertEquals("", adapter.formatDate(null, "en", "JP", "", ""));
    }

    @Test
    void skipsBlankTrustCodeAndSchemeTypeInLookup() {
        Map<String, String> enFormats = new LinkedHashMap<>();
        enFormats.put("JP", "MM/dd/yyyy");
        enFormats.put("JP.", "should-not-match");
        var adapter = adapterWith(Map.of("en", enFormats));

        // Should match JP, not JP. or JP..
        var result = adapter.formatDate(LocalDate.of(2026, 3, 1), "en", "JP", "", "");
        assertEquals("03/01/2026", result);
    }

    @Test
    void formatsWithZhHkLocale() {
        var adapter = adapterWith(Map.of(
                "en", Map.of("default", "dd/MM/yyyy"),
                "zh_HK", Map.of("default", "yyyy-MM-dd")));

        var result = adapter.formatDate(LocalDate.of(2026, 3, 1), "zh_HK", "JP", "", "");
        assertEquals("2026-03-01", result);
    }
}
