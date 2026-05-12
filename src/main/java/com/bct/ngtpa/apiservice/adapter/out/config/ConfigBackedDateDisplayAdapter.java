package com.bct.ngtpa.apiservice.adapter.out.config;

import com.bct.ngtpa.apiservice.application.port.out.DateDisplayPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Config-backed implementation of {@link DateDisplayPort}.
 *
 * <p>Resolves the display format using the following fallback order for a given locale:
 * <ol>
 *   <li>{@code ${env}.${trustCode}.${schemeType}}</li>
 *   <li>{@code ${env}.${trustCode}}</li>
 *   <li>{@code ${env}}</li>
 *   <li>{@code ${trustCode}.${schemeType}}</li>
 *   <li>{@code ${trustCode}}</li>
 *   <li>{@code *} (wildcard fallback)</li>
 * </ol>
 *
 * <p>Segments that are blank are skipped; compound keys are only emitted when all
 * constituent segments are present. If the requested locale has no matching entry,
 * the resolver retries with the {@code en} locale. Falls back to ISO {@code yyyy-MM-dd}
 * if no config entry is found at all.
 */
@Component
@RequiredArgsConstructor
public class ConfigBackedDateDisplayAdapter implements DateDisplayPort {

    private static final String WILDCARD_KEY = "*";
    private static final DateTimeFormatter ISO_FALLBACK = DateTimeFormatter.ISO_LOCAL_DATE;

    private final DateFormatProperties dateFormatProperties;

    @Override
    public String formatDate(LocalDate date, String lang, String env, String trustCode, String schemeType) {
        if (date == null) {
            return "";
        }
        var pattern = resolvePattern(lang, env, trustCode, schemeType);
        if (pattern == null) {
            return ISO_FALLBACK.format(date);
        }
        return date.format(DateTimeFormatter.ofPattern(pattern));
    }

    private String resolvePattern(String lang, String env, String trustCode, String schemeType) {
        var normalizedLang = StringUtils.hasText(lang) ? lang.trim() : "en";
        var candidates = candidateKeys(env, trustCode, schemeType);

        // First pass: requested locale
        Map<String, String> localeFormats = dateFormatProperties.getLocaleFormats(normalizedLang);
        if (!localeFormats.isEmpty()) {
            for (var candidate : candidates) {
                var p = localeFormats.get(candidate);
                if (StringUtils.hasText(p)) {
                    return p;
                }
            }
        }

        // Second pass: fall back to English when the requested locale is missing or has no match
        if (!"en".equals(normalizedLang)) {
            var enFormats = dateFormatProperties.getLocaleFormats("en");
            for (var candidate : candidates) {
                var p = enFormats.get(candidate);
                if (StringUtils.hasText(p)) {
                    return p;
                }
            }
        }

        return null;
    }

    private List<String> candidateKeys(String env, String trustCode, String schemeType) {
        boolean hasEnv = StringUtils.hasText(env);
        boolean hasTrustCode = StringUtils.hasText(trustCode);
        boolean hasSchemeType = StringUtils.hasText(schemeType);

        List<String> candidates = new ArrayList<>();

        if (hasEnv && hasTrustCode && hasSchemeType) {
            candidates.add(env.trim() + "." + trustCode.trim() + "." + schemeType.trim());
        }
        if (hasEnv && hasTrustCode) {
            candidates.add(env.trim() + "." + trustCode.trim());
        }
        if (hasEnv) {
            candidates.add(env.trim());
        }
        if (hasTrustCode && hasSchemeType) {
            candidates.add(trustCode.trim() + "." + schemeType.trim());
        }
        if (hasTrustCode) {
            candidates.add(trustCode.trim());
        }
        candidates.add(WILDCARD_KEY);

        return List.copyOf(candidates);
    }
}
