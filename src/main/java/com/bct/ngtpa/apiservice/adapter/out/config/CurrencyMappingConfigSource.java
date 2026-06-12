package com.bct.ngtpa.apiservice.adapter.out.config;

import com.bct.ngtpa.apiservice.shared.config.ConfigCategory;
import com.bct.ngtpa.apiservice.shared.config.ConfigSource;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Locale;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class CurrencyMappingConfigSource implements ConfigSource {

    private static final String DEFAULT_LANGUAGE = Locale.ENGLISH.toString();

    private final CurrencyMappingProperties currencyMappingProperties;

    @Override
    public ConfigCategory category() {
        return ConfigCategory.CURRENCY_MAPPING;
    }

    @Override
    public Optional<String> get(String key, Locale locale) {
        if (!StringUtils.hasText(key)) {
            return Optional.empty();
        }

        var normalizedKey = key.trim();
        var language = normalizeLocaleKey(locale);
        var resolved = find(language, normalizedKey);
        if (resolved.isPresent() || DEFAULT_LANGUAGE.equals(language)) {
            return resolved;
        }

        return find(DEFAULT_LANGUAGE, normalizedKey);
    }

    private Optional<String> find(String language, String key) {
        return Optional.ofNullable(currencyMappingProperties.getLocaleFormats(language).get(key))
                .filter(StringUtils::hasText);
    }

    private String normalizeLocaleKey(Locale locale) {
        if (locale == null || !StringUtils.hasText(locale.toString())) {
            return DEFAULT_LANGUAGE;
        }
        return locale.toString();
    }
}
