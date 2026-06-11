package com.bct.ngtpa.apiservice.adapter.out.config;

import com.bct.ngtpa.apiservice.shared.config.ConfigCategory;
import com.bct.ngtpa.apiservice.shared.config.ConfigSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Locale;
import java.util.Optional;

@Component
public class ErrorMessageConfigSource implements ConfigSource {

    private static final String DEFAULT_LOCALE_KEY = Locale.ENGLISH.toString();

    private final ErrorMessageProperties properties;

    public ErrorMessageConfigSource(ErrorMessageProperties properties) {
        this.properties = properties;
    }

    @Override
    public ConfigCategory category() {
        return ConfigCategory.ERROR_MESSAGE;
    }

    @Override
    public Optional<String> get(String key, Locale locale) {
        if (!StringUtils.hasText(key)) {
            return Optional.empty();
        }

        return properties.find(normalizeLocaleKey(locale), key);
    }

    private static String normalizeLocaleKey(Locale locale) {
        if (locale == null || !StringUtils.hasText(locale.toString())) {
            return DEFAULT_LOCALE_KEY;
        }

        return locale.toString();
    }
}
