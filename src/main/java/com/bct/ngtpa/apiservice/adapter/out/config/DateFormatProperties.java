package com.bct.ngtpa.apiservice.adapter.out.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Configuration properties for date display formatting.
 *
 * <p>Structure: locale → (env-key → DateTimeFormatter pattern string).
 * The special key {@code default} is used as the fallback format for a given locale.
 *
 * <p>Example YAML:
 * <pre>
 * display-format:
 *   date:
 *     en:
 *       default: dd/MM/yyyy
 *       JP: MM/dd/yyyy
 *     zh_HK:
 *       default: yyyy-MM-dd
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
