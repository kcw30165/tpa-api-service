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
 *   <li>{@code default}</li>
 * </ol>
 *
 * <p>Malformed keys caused by blank trustCode/schemeType are skipped.
 * Falls back to ISO {@code yyyy-MM-dd} if no config entry is found.
 */
@Component
@RequiredArgsConstructor
public class ConfigBackedDateDisplayAdapter implements DateDisplayPort {

    private static final String DEFAULT_KEY = "default";
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
        Map<String, String> localeFormats = dateFormatProperties.getLocaleFormats(normalizedLang);

        if (localeFormats.isEmpty()) {
            // Try "en" as a safe fallback locale
            localeFormats = dateFormatProperties.getLocaleFormats("en");
        }

        for (var candidate : candidateKeys(env, trustCode, schemeType)) {
            var p = localeFormats.get(candidate);
            if (StringUtils.hasText(p)) {
                return p;
            }
        }

        return null;
    }

    private List<String> candidateKeys(String env, String trustCode, String schemeType) {
        List<String> suffixSegments = new ArrayList<>();
        addIfPresent(suffixSegments, env);
        addIfPresent(suffixSegments, trustCode);
        addIfPresent(suffixSegments, schemeType);

        List<String> candidates = new ArrayList<>();
        for (int size = suffixSegments.size(); size > 0; size--) {
            candidates.add(String.join(".", suffixSegments.subList(0, size)));
        }
        candidates.add(DEFAULT_KEY);
        return List.copyOf(candidates);
    }

    private void addIfPresent(List<String> segments, String value) {
        if (StringUtils.hasText(value)) {
            segments.add(value.trim());
        }
    }
}
