package com.bct.ngtpa.apiservice.adapter.out.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.Map;

@ConfigurationProperties(prefix = "currency-mapping")
public class CurrencyMappingProperties extends LinkedHashMap<String, Map<String, String>> {

    public Map<String, String> getLocaleMappings(String locale) {
        if (!StringUtils.hasText(locale)) {
            return Map.of();
        }

        var mappings = get(locale.trim());
        return mappings == null ? Map.of() : mappings;
    }
}
