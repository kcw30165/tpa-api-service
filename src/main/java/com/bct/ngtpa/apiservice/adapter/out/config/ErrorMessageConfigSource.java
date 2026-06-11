package com.bct.ngtpa.apiservice.adapter.out.config;

import com.bct.ngtpa.apiservice.shared.config.ConfigCategory;
import com.bct.ngtpa.apiservice.shared.config.ConfigSource;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Locale;
import java.util.Optional;

@Component
public class ErrorMessageConfigSource implements ConfigSource {

    private static final String PREFIX = "error-message.";

    private final Environment environment;

    public ErrorMessageConfigSource(Environment environment) {
        this.environment = environment;
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

        var localeKey = locale == null || locale.toString().isBlank() ? Locale.ENGLISH.toString() : locale.toString();
        return Optional.ofNullable(environment.getProperty(PREFIX + localeKey + "." + key))
                .filter(StringUtils::hasText);
    }
}