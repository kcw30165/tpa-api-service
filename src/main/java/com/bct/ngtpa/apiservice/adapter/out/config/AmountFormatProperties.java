package com.bct.ngtpa.apiservice.adapter.out.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Configuration properties for amount/number display formatting.
 *
 * <p>Structure: locale → (env-key → {@link AmountFormatConfig}).
 * The special key {@code default} is used as the fallback format for a given locale.
 *
 * <p>Example YAML:
 * <pre>
 * display-format:
 *   amount:
 *     en:
 *       default:
 *         min-fraction-digits: 0
 *         max-fraction-digits: 2
 *         grouping-separator: ","
 *         decimal-separator: "."
 *         rounding-mode: HALF_UP
 *         strip-trailing-zeros: true
 *         negative-style: minus
 * </pre>
 */
@ConfigurationProperties(prefix = "display-format.amount")
public class AmountFormatProperties extends LinkedHashMap<String, Map<String, AmountFormatConfig>> {

    public Map<String, AmountFormatConfig> getLocaleFormats(String locale) {
        if (locale == null || locale.isBlank()) {
            return Map.of();
        }
        var formats = get(locale.trim());
        return formats == null ? Map.of() : formats;
    }
}
