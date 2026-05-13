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

    private static final String PREFIX = "currency-mapping.";

    private final CurrencyMappingProperties currencyMappingProperties;

    @Override
    public ConfigCategory category() {
        return ConfigCategory.CURRENCY_MAPPING;
    }

    @Override
    public Optional<String> get(String key, Locale locale) {
        if (!StringUtils.hasText(key) || !key.startsWith(PREFIX)) {
            return Optional.empty();
        }

        var remainder = key.substring(PREFIX.length());
        var localeSeparator = remainder.indexOf('.');
        if (localeSeparator < 0) {
            return Optional.empty();
        }

        var language = remainder.substring(0, localeSeparator);
        var mappingKey = remainder.substring(localeSeparator + 1);
        return Optional.ofNullable(currencyMappingProperties.getLocaleMappings(language).get(mappingKey))
                .filter(StringUtils::hasText);
    }
}