package com.bct.ngtpa.apiservice.adapter.out.config;

import com.bct.ngtpa.apiservice.application.dto.CurrencyDisplay;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ConfigBackedCurrencyDisplayAdapterTest {

    private static ConfigBackedCurrencyDisplayAdapter adapterWithMappings(
            Map<String, String> englishMappings) {
        var properties = new LocalizedConfigProperties();
        properties.put("en", englishMappings);
        return new ConfigBackedCurrencyDisplayAdapter(properties);
    }

    private static ConfigBackedCurrencyDisplayAdapter adapterWithBothLocales(
            Map<String, String> englishMappings,
            Map<String, String> chineseMappings) {
        var properties = new LocalizedConfigProperties();
        properties.put("en", englishMappings);
        properties.put("zh_HK", chineseMappings);
        return new ConfigBackedCurrencyDisplayAdapter(properties);
    }

    @Test
    void prefersFullyQualifiedKeyFirst() {
        Map<String, String> english = new LinkedHashMap<>();
        english.put("HKD", "HKD");
        english.put("HKD.JP", "HKD JP");
        english.put("HKD.JP.TRUST", "HKD TRUST");
        english.put("HKD.JP.TRUST.SCHEME", "HKD SCHEME");
        var adapter = adapterWithMappings(english);

        CurrencyDisplay result = adapter.resolveCurrencyDisplay("HKD", "JP", "TRUST", "SCHEME");
        assertEquals("HKD SCHEME", result.en());
    }

    @Test
    void fallsBackToCodeDotEnvDotTrustWhenSchemeAbsent() {
        Map<String, String> english = new LinkedHashMap<>();
        english.put("HKD", "HKD");
        english.put("HKD.JP", "HKD JP");
        english.put("HKD.JP.TRUST", "HKD TRUST");
        var adapter = adapterWithMappings(english);

        CurrencyDisplay result = adapter.resolveCurrencyDisplay("HKD", "JP", "TRUST", "");
        assertEquals("HKD TRUST", result.en());
    }

    @Test
    void fallsBackToCodeDotEnvWhenTrustAndSchemeAbsent() {
        Map<String, String> english = new LinkedHashMap<>();
        english.put("HKD", "HKD");
        english.put("HKD.JP", "HKD JP");
        var adapter = adapterWithMappings(english);

        CurrencyDisplay result = adapter.resolveCurrencyDisplay("HKD", "JP", "", "");
        assertEquals("HKD JP", result.en());
    }

    @Test
    void fallsBackToCodeOnlyWhenNoQualifiersMatch() {
        Map<String, String> english = new LinkedHashMap<>();
        english.put("HKD", "HKD");
        var adapter = adapterWithMappings(english);

        CurrencyDisplay result = adapter.resolveCurrencyDisplay("HKD", "", "", "");
        assertEquals("HKD", result.en());
    }

    @Test
    void fallsBackToRawTrimmedCodeWhenNoMappingFound() {
        var adapter = adapterWithMappings(new LinkedHashMap<>());

        CurrencyDisplay result = adapter.resolveCurrencyDisplay(" USD ", "JP", "", "");
        assertEquals("USD", result.en());
    }

    @Test
    void returnsEmptyStringForBlankCode() {
        var adapter = adapterWithMappings(new LinkedHashMap<>());

        CurrencyDisplay result = adapter.resolveCurrencyDisplay("   ", "JP", "", "");
        assertEquals("", result.en());
        assertEquals("", result.zh());
    }

    @Test
    void returnsEmptyStringForNullCode() {
        var adapter = adapterWithMappings(new LinkedHashMap<>());

        CurrencyDisplay result = adapter.resolveCurrencyDisplay(null, "JP", "", "");
        assertEquals("", result.en());
        assertEquals("", result.zh());
    }

    @Test
    void resolvesBothLocalesIndependently() {
        Map<String, String> english = new LinkedHashMap<>();
        english.put("HKD", "Hong Kong Dollar");
        Map<String, String> chinese = new LinkedHashMap<>();
        chinese.put("HKD", "港元");

        var adapter = adapterWithBothLocales(english, chinese);

        CurrencyDisplay result = adapter.resolveCurrencyDisplay("HKD", "", "", "");
        assertEquals("Hong Kong Dollar", result.en());
        assertEquals("港元", result.zh());
    }

    @Test
    void returnsRawCodeForBothLocalesWhenNoMappingExists() {
        var adapter = adapterWithBothLocales(new LinkedHashMap<>(), new LinkedHashMap<>());

        CurrencyDisplay result = adapter.resolveCurrencyDisplay("HKD", "JP", "TRUST", "SCHEME");
        assertEquals("HKD", result.en());
        assertEquals("HKD", result.zh());
    }

    @Test
    void fallbsBackToCodeWhentrustScheme() {
        Map<String, String> english = new LinkedHashMap<>();
        english.put("HKD", "HKD");
        english.put("HKD.TRUST.SCHEME", "HKD TRUST SCHEME");

        var adapter = adapterWithMappings(english);

        CurrencyDisplay result = adapter.resolveCurrencyDisplay("HKD", "", "TRUST", "SCHEME");
        assertEquals("HKD", result.en());
    }

    @Test
    void fallsBackToEnglishMappingForZhWhenChineseLocaleMissing() {
        Map<String, String> english = new LinkedHashMap<>();
        english.put("HKD.TRUST", "Hong Kong Dollar");

        var adapter = adapterWithBothLocales(english, new LinkedHashMap<>());

        CurrencyDisplay result = adapter.resolveCurrencyDisplay("HKD", "", "", "TRUST");
        assertEquals("Hong Kong Dollar", result.en());
        assertEquals("Hong Kong Dollar", result.zh());
    }
}
