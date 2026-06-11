package com.bct.ngtpa.apiservice.adapter.out.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Properties-backed error message configuration.
 *
 * <p>The YAML shape intentionally matches {@code currency-mapping}, not
 * {@code display-format}:</p>
 *
 * <pre>
 * error-message:
 *   en:
 *     "err.request.invalid": "Invalid request."
 *   zh_HK:
 *     "err.request.invalid": "請求無效。"
 * </pre>
 *
 * <p>This class extends {@link LinkedHashMap} so Spring Boot can bind the
 * dynamic locale keys directly under {@code error-message}.</p>
 */
@Component
@ConfigurationProperties(prefix = "error-message")
public class ErrorMessageProperties extends LinkedHashMap<String, Map<String, String>> {

    public Optional<String> find(String localeKey, String messageKey) {
        if (!StringUtils.hasText(localeKey) || !StringUtils.hasText(messageKey)) {
            return Optional.empty();
        }

        return Optional.ofNullable(get(localeKey.trim()))
                .map(messages -> messages.get(messageKey.trim()))
                .filter(StringUtils::hasText);
    }
}
