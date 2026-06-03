package com.bct.ngtpa.apiservice.shared.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.Locale;
import org.junit.jupiter.api.Test;

class ConfigLookupContextAdditionalTest {

    @Test
    void trimsBlankSegmentsToNullAndDefaultsNullLocaleToEnglish() {
        ConfigLookupContext context = ConfigLookupContext.of("  ", " JPM ", "", (Locale) null);

        assertNull(context.accountEnv());
        assertEquals("JPM", context.trustCode());
        assertNull(context.schemeType());
        assertEquals(Locale.ENGLISH, context.locale());
        assertEquals("en", context.localeKey());
    }

    @Test
    void parsesLocaleStringWithUnderscoreAsLanguageTag() {
        ConfigLookupContext context = ConfigLookupContext.of("JP", "JPM", "OE", "zh_HK");

        assertEquals("zh_HK", context.localeKey());
    }

    @Test
    void blankLocaleStringFallsBackToEnglish() {
        ConfigLookupContext context = ConfigLookupContext.of("JP", "JPM", "OE", "   ");

        assertEquals(Locale.ENGLISH, context.locale());
        assertEquals("en", context.localeKey());
    }
}
