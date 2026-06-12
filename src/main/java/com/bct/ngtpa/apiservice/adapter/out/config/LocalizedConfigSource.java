package com.bct.ngtpa.apiservice.adapter.out.config;

import com.bct.ngtpa.apiservice.shared.config.ConfigCategory;
import com.bct.ngtpa.apiservice.shared.config.ConfigSource;
import org.springframework.util.StringUtils;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

public class LocalizedConfigSource implements ConfigSource {

    private static final String DEFAULT_LANGUAGE = Locale.ENGLISH.toString();

    private final ConfigCategory category;
    private final LocalizedConfigProperties properties;
    private final String baseCode;

    public LocalizedConfigSource(ConfigCategory category, LocalizedConfigProperties properties) {
        this(category, properties, null);
    }

    public LocalizedConfigSource(
            ConfigCategory category,
            LocalizedConfigProperties properties,
            String baseCode) {
        this.category = Objects.requireNonNull(category, "category must not be null");
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
        this.baseCode = normalizeBaseCode(baseCode);
    }

    @Override
    public ConfigCategory category() {
        return category;
    }

    @Override
    public Optional<String> get(String key, Locale locale) {
        if (!StringUtils.hasText(key)) {
            return Optional.empty();
        }

        var normalizedKey = key.trim();
        if (!supportsKey(normalizedKey)) {
            return Optional.empty();
        }

        var language = normalizeLocaleKey(locale);
        var resolved = find(language, normalizedKey);
        if (resolved.isPresent() || DEFAULT_LANGUAGE.equals(language)) {
            return resolved;
        }

        return find(DEFAULT_LANGUAGE, normalizedKey);
    }

    private Optional<String> find(String language, String key) {
        return Optional.ofNullable(properties.getLocaleFormats(language).get(key))
                .filter(StringUtils::hasText);
    }

    private boolean supportsKey(String key) {
        return baseCode == null || baseCode.equals(key) || key.startsWith(baseCode + ".");
    }

    private String normalizeLocaleKey(Locale locale) {
        if (locale == null || !StringUtils.hasText(locale.toString())) {
            return DEFAULT_LANGUAGE;
        }
        return locale.toString();
    }

    private String normalizeBaseCode(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }
}
