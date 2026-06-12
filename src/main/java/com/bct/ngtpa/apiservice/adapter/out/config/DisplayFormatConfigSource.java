package com.bct.ngtpa.apiservice.adapter.out.config;

import com.bct.ngtpa.apiservice.shared.config.ConfigCategory;
import com.bct.ngtpa.apiservice.shared.config.ConfigSource;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class DisplayFormatConfigSource implements ConfigSource {

    private static final String PREFIX = "display-format.";

    private final DateFormatProperties dateFormatProperties;
    private final AmountFormatProperties amountFormatProperties;

    @Override
    public ConfigCategory category() {
        return ConfigCategory.DISPLAY_FORMAT;
    }

    @Override
    public Optional<String> get(String key, Locale locale) {
        if (!StringUtils.hasText(key) || !key.startsWith(PREFIX)) {
            return Optional.empty();
        }

        var remainder = key.substring(PREFIX.length());
        var codeSeparator = remainder.indexOf('.');
        if (codeSeparator < 0) {
            return Optional.empty();
        }
        var localeSeparator = remainder.indexOf('.', codeSeparator + 1);
        if (localeSeparator < 0) {
            return Optional.empty();
        }

        var code = remainder.substring(0, codeSeparator);
        var language = remainder.substring(codeSeparator + 1, localeSeparator);
        var variantKey = remainder.substring(localeSeparator + 1);

        var formats = resolveLocaleFormats(code, language);
        return Optional.ofNullable(resolveFormat(formats, code, variantKey))
                .filter(StringUtils::hasText);
    }

    private String resolveFormat(Map<String, String> formats, String code, String variantKey) {
        if (formats.isEmpty() || !StringUtils.hasText(variantKey)) {
            return null;
        }

        var normalizedVariantKey = variantKey.trim();
        if ("*".equals(normalizedVariantKey)) {
            return firstNonBlank(formats.get(code), formats.get("*"));
        }

        return firstNonBlank(
                formats.get(code + "." + normalizedVariantKey),
                formats.get(normalizedVariantKey));
    }

    private String firstNonBlank(String first, String second) {
        return StringUtils.hasText(first) ? first : second;
    }

    private Map<String, String> resolveLocaleFormats(String code, String language) {
        return switch (code) {
            case "date" -> dateFormatProperties.getLocaleFormats(language);
            case "amount" -> amountFormatProperties.getLocaleFormats(language);
            default -> Map.of();
        };
    }
}