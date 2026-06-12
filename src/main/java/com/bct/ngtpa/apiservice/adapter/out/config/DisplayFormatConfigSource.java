package com.bct.ngtpa.apiservice.adapter.out.config;

import com.bct.ngtpa.apiservice.shared.config.ConfigCategory;
import com.bct.ngtpa.apiservice.shared.config.ConfigSource;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class DisplayFormatConfigSource implements ConfigSource {

    private static final String DEFAULT_LANGUAGE = Locale.ENGLISH.toString();
    private static final String DATE_CODE = "date";
    private static final String AMOUNT_CODE = "amount";
    private static final String WILDCARD_KEY = "*";

    private final DateFormatProperties dateFormatProperties;
    private final AmountFormatProperties amountFormatProperties;

    @Override
    public ConfigCategory category() {
        return ConfigCategory.DISPLAY_FORMAT;
    }

    @Override
    public Optional<String> get(String key, Locale locale) {
        if (!StringUtils.hasText(key)) {
            return Optional.empty();
        }

        var normalizedKey = key.trim();
        var code = resolveCode(normalizedKey);
        if (code == null) {
            return Optional.empty();
        }

        var language = normalizeLocaleKey(locale);
        var resolved = resolveForLanguage(code, normalizedKey, language);
        if (resolved.isPresent() || DEFAULT_LANGUAGE.equals(language)) {
            return resolved;
        }

        return resolveForLanguage(code, normalizedKey, DEFAULT_LANGUAGE);
    }

    private Optional<String> resolveForLanguage(String code, String key, String language) {
        var formats = resolveLocaleFormats(code, language);
        if (formats.isEmpty()) {
            return Optional.empty();
        }

        return Optional.ofNullable(resolveFormat(formats, code, key))
                .filter(StringUtils::hasText);
    }

    private String resolveFormat(Map<String, String> formats, String code, String key) {
        var configured = formats.get(key);
        if (StringUtils.hasText(configured)) {
            return configured;
        }

        if (code.equals(key)) {
            return formats.get(WILDCARD_KEY);
        }

        var legacyVariantKey = key.substring(code.length() + 1);
        return formats.get(legacyVariantKey);
    }

    private String resolveCode(String key) {
        if (DATE_CODE.equals(key) || key.startsWith(DATE_CODE + ".")) {
            return DATE_CODE;
        }
        if (AMOUNT_CODE.equals(key) || key.startsWith(AMOUNT_CODE + ".")) {
            return AMOUNT_CODE;
        }
        return null;
    }

    private Map<String, String> resolveLocaleFormats(String code, String language) {
        return switch (code) {
            case DATE_CODE -> dateFormatProperties.getLocaleFormats(language);
            case AMOUNT_CODE -> amountFormatProperties.getLocaleFormats(language);
            default -> Map.of();
        };
    }

    private String normalizeLocaleKey(Locale locale) {
        if (locale == null || !StringUtils.hasText(locale.toString())) {
            return DEFAULT_LANGUAGE;
        }
        return locale.toString();
    }
}
