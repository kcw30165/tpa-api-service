package com.bct.ngtpa.apiservice.adapter.out.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Configuration properties for date display formatting.
 *
 * <p>Structure: locale → (key → DateTimeFormatter pattern string).
 * The special key {@code *} is the wildcard fallback for a given locale.
 * In YAML the wildcard key must be quoted as {@code "[*]"} so Spring Boot binds it
 * as the literal map key {@code *}.
 *
 * <p>Example YAML:
 * <pre>
 * display-format:
 *   date:
 *     en:
 *       "[*]": dd/MM/yyyy
 *       JP: MM/dd/yyyy
 *     zh_HK:
 *       "[*]": dd/MM/yyyy
 * </pre>
 */
@ConfigurationProperties(prefix = "display-format.date")
public class DateFormatProperties extends LinkedHashMap<String, Map<String, String>> {

    public Map<String, String> getLocaleFormats(String locale) {
        if (locale == null || locale.isBlank()) {
            return Map.of();
        }
        var formats = get(locale.trim());
        return formats == null ? Map.of() : formats;
    }
}
