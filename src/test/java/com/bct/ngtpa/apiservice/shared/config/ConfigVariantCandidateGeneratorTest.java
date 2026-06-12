package com.bct.ngtpa.apiservice.shared.config;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ConfigVariantCandidateGeneratorTest {

    private final ConfigVariantCandidateGenerator generator = new ConfigVariantCandidateGenerator();

    @Test
    void generatesAllCombinationsInRequiredOrder() {
        var context = ConfigLookupContext.of("PROD", "RM", "MPF", Locale.ENGLISH);

        assertEquals(List.of(
                "PROD.RM.MPF",
                "PROD.RM",
                "PROD.MPF",
                "RM.MPF",
                "PROD",
                "RM",
                "MPF"), generator.generate(context));
    }

    @Test
    void skipsMissingSchemeType() {
        var context = ConfigLookupContext.of("PROD", "RM", "   ", Locale.ENGLISH);

        assertEquals(List.of("PROD.RM", "PROD", "RM"), generator.generate(context));
    }

    @Test
    void skipsMissingAccountEnv() {
        var context = ConfigLookupContext.of("  ", "RM", "MPF", Locale.ENGLISH);

        assertEquals(List.of("RM.MPF", "RM", "MPF"), generator.generate(context));
    }

    @Test
    void returnsSingleAccountEnvWhenOnlyAccountEnvIsPresent() {
        var context = ConfigLookupContext.of("PROD", null, null, Locale.ENGLISH);

        assertEquals(List.of("PROD"), generator.generate(context));
    }

    @Test
    void returnsSingleTrustCodeWhenOnlyTrustCodeIsPresent() {
        var context = ConfigLookupContext.of(null, "RM", null, Locale.ENGLISH);

        assertEquals(List.of("RM"), generator.generate(context));
    }

    @Test
    void returnsSingleSchemeTypeWhenOnlySchemeTypeIsPresent() {
        var context = ConfigLookupContext.of(null, null, "MPF", Locale.ENGLISH);

        assertEquals(List.of("MPF"), generator.generate(context));
    }

    @Test
    void returnsEmptyListWhenAllDimensionsBlank() {
        var context = ConfigLookupContext.of(" ", "", "  ", Locale.ENGLISH);

        assertEquals(List.of(), generator.generate(context));
    }

    @Test
    void removesDuplicateCandidatesWhilePreservingOrder() {
        var context = ConfigLookupContext.of("RM", "RM", "RM", Locale.ENGLISH);

        assertEquals(List.of("RM.RM.RM", "RM.RM", "RM"), generator.generate(context));
    }

    @Test
    void trimsValuesAndAvoidsMalformedKeys() {
        var context = ConfigLookupContext.of(" PROD ", " RM ", " MPF ", Locale.ENGLISH);

        assertEquals(List.of(
                "PROD.RM.MPF",
                "PROD.RM",
                "PROD.MPF",
                "RM.MPF",
                "PROD",
                "RM",
                "MPF"), generator.generate(context));
    }

    @Test
    void configLookupContextExposesAccountEnvAccessor() {
        var context = ConfigLookupContext.of("JP", "RM", "MPF", Locale.ENGLISH);

        assertEquals("JP", context.accountEnv());
    }
}