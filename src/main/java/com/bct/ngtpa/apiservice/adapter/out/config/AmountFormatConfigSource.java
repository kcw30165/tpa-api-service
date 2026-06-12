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
public class AmountFormatConfigSource implements ConfigSource {

    private static final String DEFAULT_LANGUAGE = Locale.ENGLISH.toString();
    private static final String CODE = "amount";
    private static final String WILDCARD_KEY = "*";

    private final AmountFormatProperties amountFormatProperties;

    @Override
    public ConfigCategory category() {
        return ConfigCategory.DISPLAY_AMOUNT_FORMAT;
    }

    @Override
    public Optional<String> get(String key, Locale locale) {
        if (!StringUtils.hasText(key)) {
            return Optional.empty();
        }

        var normalizedKey = key.trim();
        if (!CODE.equals(normalizedKey) && !normalizedKey.startsWith(CODE + ".")) {
            return Optional.empty();
        }

        var language = normalizeLocaleKey(locale);
        var resolved = resolveForLanguage(normalizedKey, language);
        if (resolved.isPresent() || DEFAULT_LANGUAGE.equals(language)) {
            return resolved;
        }

        return resolveForLanguage(normalizedKey, DEFAULT_LANGUAGE);
    }

    private Optional<String> resolveForLanguage(String key, String language) {
        var formats = amountFormatProperties.getLocaleFormats(language);
        if (formats.isEmpty()) {
            return Optional.empty();
        }

        return Optional.ofNullable(resolveFormat(formats, key))
                .filter(StringUtils::hasText);
    }

    private String resolveFormat(Map<String, String> formats, String key) {
        var configured = formats.get(key);
        if (StringUtils.hasText(configured)) {
            return configured;
        }

        if (CODE.equals(key)) {
            return formats.get(WILDCARD_KEY);
        }

        var legacyVariantKey = key.substring(CODE.length() + 1);
        return formats.get(legacyVariantKey);
    }

    private String normalizeLocaleKey(Locale locale) {
        if (locale == null || !StringUtils.hasText(locale.toString())) {
            return DEFAULT_LANGUAGE;
        }
        return locale.toString();
    }
}
