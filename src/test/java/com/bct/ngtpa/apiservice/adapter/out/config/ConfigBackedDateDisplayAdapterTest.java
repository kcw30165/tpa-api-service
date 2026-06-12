package com.bct.ngtpa.apiservice.adapter.out.config;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ConfigBackedDateDisplayAdapterTest {

    private static ConfigBackedDateDisplayAdapter adapterWith(Map<String, Map<String, String>> data) {
        var props = new LocalizedConfigProperties();
        props.putAll(data);
        return new ConfigBackedDateDisplayAdapter(props);
    }

    // --- (a) base date fallback ---

    @Test
    void formatsDateUsingBaseDateEnPattern() {
        var adapter = adapterWith(Map.of("en", Map.of("date", "dd/MM/yyyy")));
        // trustCode=RM has no specific entry; base date applies
        var result = adapter.formatDate(LocalDate.of(2026, 3, 1), "en", "", "RM", "");
        assertEquals("01/03/2026", result);
    }

    @Test
    void baseDateFallbackWhenTrustCodeNotInMap() {
        Map<String, String> enFormats = new LinkedHashMap<>();
        enFormats.put("date", "dd/MM/yyyy");
        enFormats.put("date.JP", "MM/dd/yyyy");
        var adapter = adapterWith(Map.of("en", enFormats));
        // trustCode=RM not in map → base date
        var result = adapter.formatDate(LocalDate.of(2026, 3, 1), "en", "", "RM", "");
        assertEquals("01/03/2026", result);
    }

    // --- (b) JP override ---

    @Test
    void prefersTrustCodeOverBaseDate() {
        Map<String, String> enFormats = new LinkedHashMap<>();
        enFormats.put("date", "dd/MM/yyyy");
        enFormats.put("date.JP", "MM/dd/yyyy");
        var adapter = adapterWith(Map.of("en", enFormats));
        // trustCode=JP matches the JP key
        var result = adapter.formatDate(LocalDate.of(2026, 3, 1), "en", "", "JP", "");
        assertEquals("03/01/2026", result);
    }

    // --- (c) unknown trustCode falls back to base date ---

    @Test
    void fallsBackToBaseDateWhenTrustCodeNotFound() {
        var adapter = adapterWith(Map.of("en", Map.of("date", "dd/MM/yyyy")));
        var result = adapter.formatDate(LocalDate.of(2026, 3, 1), "en", "", "UNKNOWN", "");
        assertEquals("01/03/2026", result);
    }

    // --- (d) unknown language falls back to English base date ---

    @Test
    void usesEnLocaleAsFallbackWhenLangNotConfigured() {
        var adapter = adapterWith(Map.of("en", Map.of("date", "dd/MM/yyyy")));
        // zh_HK not in config → first pass empty → falls back to en base date
        var result = adapter.formatDate(LocalDate.of(2026, 3, 1), "zh_HK", "", "RM", "");
        assertEquals("01/03/2026", result);
    }

    @Test
    void unknownLangFallsBackToEnglishBaseDate() {
        var adapter = adapterWith(Map.of("en", Map.of("date", "dd/MM/yyyy")));
        var result = adapter.formatDate(LocalDate.of(2026, 3, 1), "UNKNOWN_LANG", "", "RM", "");
        assertEquals("01/03/2026", result);
    }

    @Test
    void zhHkFallsBackToEnBaseDateWhenNoMatchInZhHk() {
        // zh_HK has JP entry but no base date; trustCode=RM has no match in zh_HK → en base date
        Map<String, String> zhHkFormats = new LinkedHashMap<>();
        zhHkFormats.put("date.JP", "MM/dd/yyyy");
        var adapter = adapterWith(Map.of(
                "en", Map.of("date", "dd/MM/yyyy"),
                "zh_HK", zhHkFormats));
        var result = adapter.formatDate(LocalDate.of(2026, 3, 1), "zh_HK", "", "RM", "");
        assertEquals("01/03/2026", result);
    }

    // --- null date ---

    @Test
    void returnsEmptyStringForNullDate() {
        var adapter = adapterWith(Map.of("en", Map.of("date", "dd/MM/yyyy")));
        assertEquals("", adapter.formatDate(null, "en", "", "JP", ""));
    }

    // --- ISO fallback when no pattern configured ---

    @Test
    void fallsBackToDefaultPatternWhenNoPatternConfigured() {
        var adapter = adapterWith(Map.of());
        var result = adapter.formatDate(LocalDate.of(2026, 3, 1), "en", "", "JP", "");
        assertEquals("01/03/2026", result);
    }

    // --- blank segments are not assembled into malformed keys ---

    @Test
    void envKeyMatchesWhenTrustCodeIsBlank() {
        Map<String, String> enFormats = new LinkedHashMap<>();
        enFormats.put("date.JP", "MM/dd/yyyy");
        enFormats.put("date", "dd/MM/yyyy");
        var adapter = adapterWith(Map.of("en", enFormats));
        // env=JP, trustCode blank → candidates = ["JP", "date"]; must match "JP"
        var result = adapter.formatDate(LocalDate.of(2026, 3, 1), "en", "JP", "", "");
        assertEquals("03/01/2026", result);
    }

    // --- zh_HK locale with base date ---

    @Test
    void formatsWithZhHkLocale() {
        var adapter = adapterWith(Map.of(
                "en", Map.of("date", "dd/MM/yyyy"),
                "zh_HK", Map.of("date", "yyyy-MM-dd")));
        // trustCode=JP has no zh_HK-specific entry; zh_HK base date applies
        var result = adapter.formatDate(LocalDate.of(2026, 3, 1), "zh_HK", "", "JP", "");
        assertEquals("2026-03-01", result);
    }

    // --- (e) composite key lookup ---

    @Test
    void compositeKey_envTrustCodeSchemeType_matchesFirst() {
        Map<String, String> enFormats = new LinkedHashMap<>();
        enFormats.put("date.PROD.RM.MPF", "yyyy/MM/dd");
        enFormats.put("date.RM.MPF", "MM-dd-yyyy");
        enFormats.put("date.RM", "dd-MM-yyyy");
        enFormats.put("date", "dd/MM/yyyy");
        var adapter = adapterWith(Map.of("en", enFormats));
        var result = adapter.formatDate(LocalDate.of(2026, 3, 1), "en", "PROD", "RM", "MPF");
        assertEquals("2026/03/01", result);
    }

    @Test
    void compositeKey_envTrustCode_matchesSecond() {
        Map<String, String> enFormats = new LinkedHashMap<>();
        enFormats.put("date.PROD.RM", "MM-dd-yyyy");
        enFormats.put("date.RM.MPF", "should-not-match");
        enFormats.put("date.RM", "dd-MM-yyyy");
        enFormats.put("date", "dd/MM/yyyy");
        var adapter = adapterWith(Map.of("en", enFormats));
        // PROD.RM.MPF absent → tries PROD.RM (match)
        var result = adapter.formatDate(LocalDate.of(2026, 3, 1), "en", "PROD", "RM", "MPF");
        assertEquals("03-01-2026", result);
    }

    @Test
    void compositeKey_trustCodeSchemeType_matchesThird() {
        Map<String, String> enFormats = new LinkedHashMap<>();
        enFormats.put("date.RM.MPF", "MM-dd-yyyy");
        enFormats.put("date.RM", "dd-MM-yyyy");
        enFormats.put("date", "dd/MM/yyyy");
        var adapter = adapterWith(Map.of("en", enFormats));
        // no env → PROD.RM and PROD.RM.MPF not generated; tries RM.MPF (match)
        var result = adapter.formatDate(LocalDate.of(2026, 3, 1), "en", "", "RM", "MPF");
        assertEquals("03-01-2026", result);
    }

    @Test
    void compositeKey_trustCode_matchesFourth() {
        Map<String, String> enFormats = new LinkedHashMap<>();
        enFormats.put("date.RM", "dd-MM-yyyy");
        enFormats.put("date", "dd/MM/yyyy");
        var adapter = adapterWith(Map.of("en", enFormats));
        // no RM.MPF → falls to RM
        var result = adapter.formatDate(LocalDate.of(2026, 3, 1), "en", "", "RM", "MPF");
        assertEquals("01-03-2026", result);
    }
}
