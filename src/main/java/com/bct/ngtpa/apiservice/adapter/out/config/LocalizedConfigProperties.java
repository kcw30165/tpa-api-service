package com.bct.ngtpa.apiservice.adapter.out.config;

import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.Map;

public class LocalizedConfigProperties extends LinkedHashMap<String, Map<String, String>> {

    public Map<String, String> getLocaleFormats(String locale) {
        if (!StringUtils.hasText(locale)) {
            return Map.of();
        }
        return getOrDefault(locale, Map.of());
    }
}
