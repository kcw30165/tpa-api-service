package com.bct.ngtpa.apiservice.adapter.out.config;

import com.bct.ngtpa.apiservice.shared.config.ConfigCategory;
import com.bct.ngtpa.apiservice.shared.config.ConfigLookupContext;
import com.bct.ngtpa.apiservice.shared.config.ConfigLookupRequest;
import com.bct.ngtpa.apiservice.shared.config.ConfigResolutionException;
import com.bct.ngtpa.apiservice.shared.config.ConfigVariantCandidateGenerator;
import org.junit.jupiter.api.Test;

import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefaultConfigVariantResolverTest {

    @Test
    void resolvesDisplayFormatUsingExactEnvTrustSchemeOverride() {
        var resolver = resolver(
                Map.of("en", Map.of("date.PROD.RM.MPF", "yyyy/MM/dd")),
                Map.of(),
                Map.of());

        var request = ConfigLookupRequest.optional(
                ConfigCategory.DISPLAY_FORMAT,
                "date",
                ConfigLookupContext.of("PROD", "RM", "MPF", Locale.ENGLISH));

        assertEquals("yyyy/MM/dd", resolver.resolve(request).orElseThrow());
    }

    @Test
    void resolvesDisplayFormatUsingBaseCodeFallback() {
        var resolver = resolver(
                Map.of("en", Map.of("date", "dd/MM/yyyy")),
                Map.of(),
                Map.of());

        var request = ConfigLookupRequest.optional(
                ConfigCategory.DISPLAY_FORMAT,
                "date",
                ConfigLookupContext.of("", "RM", "", Locale.ENGLISH));

        assertEquals("dd/MM/yyyy", resolver.resolve(request).orElseThrow());
    }

    @Test
    void resolvesAmountUsingLanguageFallbackToEnglish() {
        var resolver = resolver(
                Map.of(),
                Map.of("en", Map.of("amount.PROD.RM", "#,##0")),
                Map.of());

        var request = ConfigLookupRequest.optional(
                ConfigCategory.DISPLAY_FORMAT,
                "amount",
                ConfigLookupContext.of("PROD", "RM", "", Locale.forLanguageTag("zh-HK")));

        assertEquals("#,##0", resolver.resolve(request).orElseThrow());
    }

    @Test
    void returnsEmptyForInvalidDisplayFormatCodeWhenLookupIsOptional() {
        var resolver = resolver(Map.of(), Map.of(), Map.of());

        var request = ConfigLookupRequest.optional(
                ConfigCategory.DISPLAY_FORMAT,
                "unknown",
                ConfigLookupContext.of("", "", "", Locale.ENGLISH));

        assertTrue(resolver.resolve(request).isEmpty());
    }

    @Test
    void throwsForInvalidDisplayFormatCodeWhenLookupIsRequired() {
        var resolver = resolver(Map.of(), Map.of(), Map.of());

        var request = ConfigLookupRequest.required(
                ConfigCategory.DISPLAY_FORMAT,
                "unknown",
                ConfigLookupContext.of("", "", "", Locale.ENGLISH));

        assertThrows(ConfigResolutionException.class, () -> resolver.resolve(request));
    }

    @Test
    void doesNotResolveDisplayFormatFromDefaultKey() {
        var resolver = resolver(
                Map.of("en", Map.of("default", "MM/dd/yyyy")),
                Map.of(),
                Map.of());

        var request = ConfigLookupRequest.optional(
                ConfigCategory.DISPLAY_FORMAT,
                "date",
                ConfigLookupContext.of("", "JP", "", Locale.ENGLISH));

        assertFalse(resolver.resolve(request).isPresent());
    }

    @Test
    void resolvesExistingCurrencyTrustSchemeKey() {
        var resolver = resolver(
                Map.of(),
                Map.of(),
                Map.of("en", Map.of("EUR.TB.HKBU", "Euro")));

        var request = ConfigLookupRequest.optional(
                ConfigCategory.CURRENCY_MAPPING,
                "EUR",
                ConfigLookupContext.of("", "TB", "HKBU", Locale.ENGLISH));

        assertEquals("Euro", resolver.resolve(request).orElseThrow());
    }

    @Test
    void resolvesCurrencyUsingMoreSpecificEnvTrustSchemeBeforeLegacyTrustScheme() {
        var resolver = resolver(
                Map.of(),
                Map.of(),
                Map.of("en", Map.of(
                        "EUR.PROD.TB.HKBU", "Euro PROD",
                        "EUR.TB.HKBU", "Euro Legacy")));

        var request = ConfigLookupRequest.optional(
                ConfigCategory.CURRENCY_MAPPING,
                "EUR",
                ConfigLookupContext.of("PROD", "TB", "HKBU", Locale.ENGLISH));

        assertEquals("Euro PROD", resolver.resolve(request).orElseThrow());
    }

    @Test
    void resolvesCurrencyUsingConfiguredCombinationPriorityOrder() {
        var resolver = resolver(
                Map.of(),
                Map.of(),
                Map.of("en", Map.of(
                        "EUR.PROD.HKBU", "env-scheme",
                        "EUR.PROD.TB", "env-trust",
                        "EUR.TB.HKBU", "trust-scheme")));

        var request = ConfigLookupRequest.optional(
                ConfigCategory.CURRENCY_MAPPING,
                "EUR",
                ConfigLookupContext.of("PROD", "TB", "HKBU", Locale.ENGLISH));

        assertEquals("env-trust", resolver.resolve(request).orElseThrow());
    }

    @Test
    void resolvesCurrencyUsingBaseCodeFallback() {
        var resolver = resolver(
                Map.of(),
                Map.of(),
                Map.of("en", Map.of("EUR", "Euro")));

        var request = ConfigLookupRequest.optional(
                ConfigCategory.CURRENCY_MAPPING,
                "EUR",
                ConfigLookupContext.of("PROD", "TB", "HKBU", Locale.ENGLISH));

        assertEquals("Euro", resolver.resolve(request).orElseThrow());
    }

    @Test
    void resolvesCurrencyUsingEnglishLanguageFallback() {
        var resolver = resolver(
                Map.of(),
                Map.of(),
                Map.of("en", Map.of("EUR.PROD", "Euro")));

        var request = ConfigLookupRequest.optional(
                ConfigCategory.CURRENCY_MAPPING,
                "EUR",
                ConfigLookupContext.of("PROD", "", "", Locale.forLanguageTag("zh-HK")));

        assertEquals("Euro", resolver.resolve(request).orElseThrow());
    }

    private static DefaultConfigVariantResolver resolver(
            Map<String, Map<String, String>> dateFormats,
            Map<String, Map<String, String>> amountFormats,
            Map<String, Map<String, String>> currencyMappings) {
        var dateProperties = new DateFormatProperties();
        dateProperties.putAll(dateFormats);

        var amountProperties = new AmountFormatProperties();
        amountProperties.putAll(amountFormats);

        var currencyProperties = new CurrencyMappingProperties();
        currencyProperties.putAll(currencyMappings);

        var candidateGenerator = new ConfigVariantCandidateGenerator();
        return new DefaultConfigVariantResolver(
                java.util.List.of(
                        new DisplayFormatConfigSource(dateProperties, amountProperties),
                        new CurrencyMappingConfigSource(currencyProperties)),
                java.util.List.of(
                        new DisplayFormatKeyCandidateStrategy(candidateGenerator),
                        new CurrencyMappingKeyCandidateStrategy(candidateGenerator),
                        new DefaultConfigKeyCandidateStrategy(candidateGenerator)));
    }
}
