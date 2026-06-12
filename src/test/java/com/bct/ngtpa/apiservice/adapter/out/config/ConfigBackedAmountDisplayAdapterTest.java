package com.bct.ngtpa.apiservice.adapter.out.config;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ConfigBackedAmountDisplayAdapterTest {

    private static ConfigBackedAmountDisplayAdapter adapterWith(Map<String, Map<String, String>> data) {
        var props = new AmountFormatProperties();
        props.putAll(data);
        return new ConfigBackedAmountDisplayAdapter(props);
    }

    private static ConfigBackedAmountDisplayAdapter defaultAdapter() {
        return adapterWith(Map.of(
                "en", Map.of("*", "#,##0.00", "JP", "#,##0.00"),
                "zh_HK", Map.of("*", "#,##0.00", "JP", "#,##0.00")));
    }

    // --- Formatting output tests ---

    @Test
    void formatsZeroWithTwoDecimalPlaces() {
        assertEquals("0.00", defaultAdapter().formatAmount(BigDecimal.ZERO, "en", "", "", ""));
    }

    @Test
    void formatsOneWithTwoDecimalPlaces() {
        assertEquals("1.00", defaultAdapter().formatAmount(BigDecimal.ONE, "en", "", "", ""));
    }

    @Test
    void formatsTwoDecimalSingleTrailingDigit() {
        assertEquals("12.30", defaultAdapter().formatAmount(new BigDecimal("12.3"), "en", "", "", ""));
    }

    @Test
    void formatsWithThousandsSeparatorAndTwoDecimals() {
        assertEquals("1,234.50", defaultAdapter().formatAmount(new BigDecimal("1234.5"), "en", "", "", ""));
    }

    @Test
    void roundsThirdDecimalHalfUp() {
        assertEquals("1,234.57", defaultAdapter().formatAmount(new BigDecimal("1234.567"), "en", "", "", ""));
    }

    @Test
    void formatsNegativeAmount() {
        assertEquals("-1,234.50", defaultAdapter().formatAmount(new BigDecimal("-1234.5"), "en", "", "", ""));
    }

    @Test
    void treatsNullAmountAsZero() {
        assertEquals("0.00", defaultAdapter().formatAmount(null, "en", "", "", ""));
    }

    // --- Pattern lookup and fallback tests ---

    @Test
    void usesLangAndTrustCodeSpecificPattern() {
        // JP key overrides wildcard: JP gets "#,##0" (no decimals), wildcard gets "#,##0.00"
        var adapter = adapterWith(Map.of("en", Map.of("JP", "#,##0", "*", "#,##0.00")));
        // 1234.4 with pattern "#,##0" rounds down to 1,234; wildcard would give "1,234.40"
        assertEquals("1,234", adapter.formatAmount(new BigDecimal("1234.4"), "en", "", "JP", ""));
    }

    @Test
    void fallsBackToWildcardWhenTrustCodeNotFound() {
        var adapter = adapterWith(Map.of("en", Map.of("*", "#,##0.00")));
        assertEquals("1,000.00", adapter.formatAmount(new BigDecimal("1000"), "en", "", "RM", ""));
    }

    @Test
    void usesZhHkWildcardPatternForZhHkLocale() {
        var adapter = adapterWith(Map.of(
                "en", Map.of("*", "#,##0"),
                "zh_HK", Map.of("*", "#,##0.00")));
        assertEquals("1,234.50", adapter.formatAmount(new BigDecimal("1234.5"), "zh_HK", "", "RM", ""));
    }

    @Test
    void fallsBackToEnglishWildcardForUnknownLanguage() {
        var adapter = adapterWith(Map.of("en", Map.of("*", "#,##0.00")));
        assertEquals("1,234.50", adapter.formatAmount(new BigDecimal("1234.5"), "fr", "", "", ""));
    }

    @Test
    void fallsBackToHardcodedPatternWhenNoConfigFound() {
        var adapter = adapterWith(Map.of());
        assertEquals("1,234.57", adapter.formatAmount(new BigDecimal("1234.567"), "en", "", "", ""));
    }

    @Test
    void normalizesBlankLangToEnglish() {
        var adapter = adapterWith(Map.of("en", Map.of("*", "#,##0.00")));
        assertEquals("100.00", adapter.formatAmount(new BigDecimal("100"), "", "", "", ""));
        assertEquals("100.00", adapter.formatAmount(new BigDecimal("100"), null, "", "", ""));
    }

    @Test
    void normalizesBlankTrustCodeToWildcardOnly() {
        var adapter = adapterWith(Map.of("en", Map.of("*", "#,##0.00")));
        assertEquals("500.00", adapter.formatAmount(new BigDecimal("500"), "en", "", "", ""));
        assertEquals("500.00", adapter.formatAmount(new BigDecimal("500"), "en", "", null, ""));
    }

    @Test
    void resolvePatternUsesLangTrustCodeFirst() {
        var adapter = adapterWith(Map.of("en", Map.of("JP", "#,##0.00", "*", "#,##0")));
        assertEquals("#,##0.00", adapter.resolvePattern("en", "JP"));
    }

    @Test
    void resolvePatternFallsBackToWildcard() {
        var adapter = adapterWith(Map.of("en", Map.of("*", "#,##0.00")));
        assertEquals("#,##0.00", adapter.resolvePattern("en", "OG"));
    }

    @Test
    void resolvePatternFallsBackToEnWildcard() {
        var adapter = adapterWith(Map.of("en", Map.of("*", "#,##0.00")));
        assertEquals("#,##0.00", adapter.resolvePattern("zh_HK", "OG"));
    }

    @Test
    void resolvePatternReturnsFallbackPatternWhenNoConfigPresent() {
        var adapter = adapterWith(Map.of());
        assertEquals(ConfigBackedAmountDisplayAdapter.FALLBACK_PATTERN, adapter.resolvePattern("en", "JP"));
    }

    @Test
    void resolvePatternUsesEnvTrustSchemeBeforeLessSpecificCandidates() {
        var adapter = adapterWith(Map.of("en", Map.of(
                "PROD.RM.MPF", "#,##0.000",
                "PROD.MPF", "#,##0.0",
                "PROD.RM", "#,##0",
                "RM.MPF", "0.00",
                "*", "#,##0.00")));

        assertEquals("#,##0.000", adapter.resolvePattern("en", "PROD", "RM", "MPF"));
    }

    @Test
    void resolvePatternUsesEnvTrustBeforeEnvSchemeAndTrustScheme() {
        var adapter = adapterWith(Map.of("en", Map.of(
                "PROD.MPF", "#,##0.0",
                "PROD.RM", "#,##0",
                "RM.MPF", "0.00",
                "*", "#,##0.00")));

        assertEquals("#,##0", adapter.resolvePattern("en", "PROD", "RM", "MPF"));
    }
}
