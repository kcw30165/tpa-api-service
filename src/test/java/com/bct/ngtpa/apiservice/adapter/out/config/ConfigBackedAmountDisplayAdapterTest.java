package com.bct.ngtpa.apiservice.adapter.out.config;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ConfigBackedAmountDisplayAdapterTest {

    private static ConfigBackedAmountDisplayAdapter adapterWith(Map<String, Map<String, AmountFormatConfig>> data) {
        var props = new AmountFormatProperties();
        props.putAll(data);
        return new ConfigBackedAmountDisplayAdapter(props);
    }

    private static AmountFormatConfig standardConfig() {
        var c = new AmountFormatConfig();
        c.setMinFractionDigits(0);
        c.setMaxFractionDigits(2);
        c.setGroupingSeparator(",");
        c.setDecimalSeparator(".");
        c.setRoundingMode(RoundingMode.HALF_UP);
        c.setStripTrailingZeros(true);
        c.setNegativeStyle("minus");
        return c;
    }

    @Test
    void formatsIntegerAmountWithThousandsSeparator() {
        var adapter = adapterWith(Map.of("en", Map.of("default", standardConfig())));
        assertEquals("22,750", adapter.formatAmount(new BigDecimal("22750"), "en", "JP", "", ""));
    }

    @Test
    void formatsDecimalAmountStrippingTrailingZeros() {
        var adapter = adapterWith(Map.of("en", Map.of("default", standardConfig())));
        assertEquals("22,750.5", adapter.formatAmount(new BigDecimal("22750.50"), "en", "JP", "", ""));
    }

    @Test
    void formatsNegativeAmountWithMinus() {
        var adapter = adapterWith(Map.of("en", Map.of("default", standardConfig())));
        assertEquals("-1,000", adapter.formatAmount(new BigDecimal("-1000"), "en", "JP", "", ""));
    }

    @Test
    void formatsNegativeAmountWithParentheses() {
        var config = standardConfig();
        config.setNegativeStyle("parentheses");
        var adapter = adapterWith(Map.of("en", Map.of("default", config)));
        assertEquals("(1,000)", adapter.formatAmount(new BigDecimal("-1000"), "en", "JP", "", ""));
    }

    @Test
    void treatsNullAmountAsZero() {
        var adapter = adapterWith(Map.of("en", Map.of("default", standardConfig())));
        assertEquals("0", adapter.formatAmount(null, "en", "JP", "", ""));
    }

    @Test
    void prefersEnvSpecificConfigOverDefault() {
        var jpConfig = standardConfig();
        jpConfig.setMaxFractionDigits(0);
        jpConfig.setStripTrailingZeros(false);

        Map<String, AmountFormatConfig> enFormats = new LinkedHashMap<>();
        enFormats.put("default", standardConfig());
        enFormats.put("JP", jpConfig);
        var adapter = adapterWith(Map.of("en", enFormats));

        // JP config: no decimals (22750.4 rounds down to 22750)
        assertEquals("22,750", adapter.formatAmount(new BigDecimal("22750.4"), "en", "JP", "", ""));
    }

    @Test
    void fallsBackToPlainStringWhenNoConfigFound() {
        var adapter = adapterWith(Map.of());
        // No config: uses FALLBACK_CONFIG (standard defaults)
        var result = adapter.formatAmount(new BigDecimal("1234.56"), "en", "JP", "", "");
        // Fallback config has minFrac=0, maxFrac=2, strip zeros, groupSep=,
        assertEquals("1,234.56", result);
    }

    @Test
    void usesEnFallbackWhenLangNotFound() {
        var adapter = adapterWith(Map.of("en", Map.of("default", standardConfig())));
        // zh_HK not configured, falls back to en
        assertEquals("1,234", adapter.formatAmount(new BigDecimal("1234"), "zh_HK", "JP", "", ""));
    }

    @Test
    void skipsBlankTrustCodeAndSchemeTypeInKeyLookup() {
        Map<String, AmountFormatConfig> enFormats = new LinkedHashMap<>();
        enFormats.put("JP", standardConfig());
        var adapter = adapterWith(Map.of("en", enFormats));

        // JP should match, not JP. or JP..
        assertEquals("100", adapter.formatAmount(new BigDecimal("100"), "en", "JP", "", ""));
    }

    @Test
    void formatsWithMinFractionDigitsPreserved() {
        var config = standardConfig();
        config.setMinFractionDigits(2);
        config.setStripTrailingZeros(false);
        var adapter = adapterWith(Map.of("en", Map.of("default", config)));

        assertEquals("100.00", adapter.formatAmount(new BigDecimal("100"), "en", "JP", "", ""));
    }
}
