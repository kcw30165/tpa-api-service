package com.bct.ngtpa.apiservice.shared.config;

import java.util.Locale;

public record ConfigLookupContext(
        String accountEnv,
        String trustCode,
        String schemeType,
        Locale locale) {

    private static final Locale DEFAULT_LOCALE = Locale.ENGLISH;

    public ConfigLookupContext {
        accountEnv = normalizeSegment(accountEnv);
        trustCode = normalizeSegment(trustCode);
        schemeType = normalizeSegment(schemeType);
        locale = normalizeLocale(locale);
    }

    public static ConfigLookupContext of(String accountEnv, String trustCode, String schemeType, Locale locale) {
        return new ConfigLookupContext(accountEnv, trustCode, schemeType, locale);
    }

    public static ConfigLookupContext of(String accountEnv, String trustCode, String schemeType, String localeValue) {
        return new ConfigLookupContext(accountEnv, trustCode, schemeType, parseLocale(localeValue));
    }

    public String localeKey() {
        var localeValue = locale.toString();
        return localeValue.isBlank() ? DEFAULT_LOCALE.toString() : localeValue;
    }

    private static String normalizeSegment(String value) {
        if (value == null) {
            return null;
        }
        var trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static Locale normalizeLocale(Locale value) {
        if (value == null) {
            return DEFAULT_LOCALE;
        }
        if (value.toString().isBlank()) {
            return DEFAULT_LOCALE;
        }
        return value;
    }

    private static Locale parseLocale(String value) {
        var normalized = normalizeSegment(value);
        if (normalized == null) {
            return DEFAULT_LOCALE;
        }

        return Locale.forLanguageTag(normalized.replace('_', '-'));
    }
}