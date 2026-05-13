package com.bct.ngtpa.apiservice.adapter.out.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Configuration properties for amount display formatting.
 *
 * <p>Structure: locale → (variant-key-or-wildcard → decimal format pattern string).
 * The special key {@code *} is the wildcard fallback for a given locale.
 *
 * <p>Example YAML:
 * <pre>
 * display-format:
 *   amount:
 *     en:
 *       "[*]": "#,##0.00"
 *       JP: "#,##0.00"
 *     zh_HK:
 *       "[*]": "#,##0.00"
 *       JP: "#,##0.00"
 * </pre>
 */
@ConfigurationProperties(prefix = "display-format.amount")
public class AmountFormatProperties extends LinkedHashMap<String, Map<String, String>> {

    public Map<String, String> getLocaleFormats(String locale) {
        if (locale == null || locale.isBlank()) {
            return Map.of();
        }
        var formats = get(locale.trim());
        return formats == null ? Map.of() : formats;
    }
}
