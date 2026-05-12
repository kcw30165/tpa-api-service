package com.bct.ngtpa.apiservice.adapter.out.config;

import com.bct.ngtpa.apiservice.application.port.out.AmountDisplayPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

/**
 * Config-backed implementation of {@link AmountDisplayPort}.
 *
 * <p>Resolves the decimal format pattern using the following fallback order:
 * <ol>
 *   <li>{@code display-format.amount.<lang>.<trustCode>}</li>
 *   <li>{@code display-format.amount.<lang>.*}</li>
 *   <li>{@code display-format.amount.en.<trustCode>}</li>
 *   <li>{@code display-format.amount.en.*}</li>
 *   <li>Hardcoded fallback: {@value FALLBACK_PATTERN}</li>
 * </ol>
 *
 * <p>The {@code env} and {@code schemeType} parameters are accepted but not used for pattern
 * resolution in this implementation; they are reserved for the future global config resolver.
 */
@Component
@RequiredArgsConstructor
public class ConfigBackedAmountDisplayAdapter implements AmountDisplayPort {

    static final String WILDCARD = "*";
    static final String DEFAULT_LANG = "en";
    static final String FALLBACK_PATTERN = "#,##0.00";

    private final AmountFormatProperties amountFormatProperties;

    @Override
    public String formatAmount(BigDecimal amount, String lang, String env, String trustCode, String schemeType) {
        var value = amount == null ? BigDecimal.ZERO : amount;
        var pattern = resolvePattern(lang, trustCode);
        return applyFormat(value, pattern);
    }

    String resolvePattern(String lang, String trustCode) {
        var normalizedLang = StringUtils.hasText(lang) ? lang.trim() : DEFAULT_LANG;
        var normalizedTrust = StringUtils.hasText(trustCode) ? trustCode.trim() : null;

        // Try lang + trustCode
        if (normalizedTrust != null) {
            var pattern = lookupPattern(normalizedLang, normalizedTrust);
            if (pattern != null) return pattern;
        }

        // Try lang + *
        var pattern = lookupPattern(normalizedLang, WILDCARD);
        if (pattern != null) return pattern;

        // Try en fallbacks only when lang is not already "en"
        if (!DEFAULT_LANG.equals(normalizedLang)) {
            if (normalizedTrust != null) {
                var enPattern = lookupPattern(DEFAULT_LANG, normalizedTrust);
                if (enPattern != null) return enPattern;
            }
            var enPattern = lookupPattern(DEFAULT_LANG, WILDCARD);
            if (enPattern != null) return enPattern;
        }

        return FALLBACK_PATTERN;
    }

    private String lookupPattern(String lang, String key) {
        var localeFormats = amountFormatProperties.getLocaleFormats(lang);
        if (localeFormats.isEmpty()) return null;
        return localeFormats.get(key);
    }

    private String applyFormat(BigDecimal value, String pattern) {
        var symbols = new DecimalFormatSymbols(Locale.ENGLISH);
        var df = new DecimalFormat(pattern, symbols);
        df.setRoundingMode(RoundingMode.HALF_UP);
        return df.format(value);
    }
}
