package com.bct.ngtpa.apiservice.adapter.out.config;

import com.bct.ngtpa.apiservice.shared.config.ConfigCategory;
import com.bct.ngtpa.apiservice.shared.config.ConfigLookupContext;
import com.bct.ngtpa.apiservice.shared.config.ConfigLookupRequest;
import com.bct.ngtpa.apiservice.shared.config.ConfigResolutionException;
import com.bct.ngtpa.apiservice.shared.config.ConfigVariantResolver;
import com.bct.ngtpa.apiservice.shared.error.ErrorCodes;
import com.bct.ngtpa.apiservice.shared.error.ErrorMessageResolver;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Locale;
import java.util.Optional;

@Component
public class ConfiguredErrorMessageResolver implements ErrorMessageResolver {

    static final String HARD_CODED_FALLBACK =
            "Sorry, this service might be interrupted. Please try again later.";

    private static final Locale ZH_HK_LOCALE = Locale.forLanguageTag("zh-HK");

    private final ConfigVariantResolver configVariantResolver;

    public ConfiguredErrorMessageResolver(ConfigVariantResolver configVariantResolver) {
        this.configVariantResolver = configVariantResolver;
    }

    @Override
    public String resolve(String errorCode, String locale, String env, String trustCode, String schemeType) {
        var normalizedLocale = normalizeLocale(locale);
        var normalizedCode = normalizeCode(errorCode);

        if (StringUtils.hasText(normalizedCode)) {
            var requested = resolveConfiguredMessage(normalizedCode, env, trustCode, schemeType, normalizedLocale);
            if (requested.isPresent()) {
                return requested.orElseThrow();
            }

            if (!Locale.ENGLISH.equals(normalizedLocale)) {
                var english = resolveConfiguredMessage(normalizedCode, env, trustCode, schemeType, Locale.ENGLISH);
                if (english.isPresent()) {
                    return english.orElseThrow();
                }
            }
        }

        return resolveConfiguredMessage(ErrorCodes.SYSTEM_UNEXPECTED, null, null, null, Locale.ENGLISH)
                .orElse(HARD_CODED_FALLBACK);
    }

    private Optional<String> resolveConfiguredMessage(
            String errorCode,
            String env,
            String trustCode,
            String schemeType,
            Locale locale) {
        try {
            return configVariantResolver.resolve(ConfigLookupRequest.optional(
                    ConfigCategory.ERROR_MESSAGE,
                    errorCode,
                    ConfigLookupContext.of(env, trustCode, schemeType, locale)))
                    .map(String::trim)
                    .filter(StringUtils::hasText);
        } catch (ConfigResolutionException exception) {
            return Optional.empty();
        }
    }

    private static String normalizeCode(String errorCode) {
        if (errorCode == null) {
            return null;
        }
        var trimmed = errorCode.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    static Locale normalizeLocale(String locale) {
        if (!StringUtils.hasText(locale)) {
            return Locale.ENGLISH;
        }

        var normalized = locale.trim().replace('_', '-');
        if (normalized.equalsIgnoreCase("en")) {
            return Locale.ENGLISH;
        }
        if (normalized.equalsIgnoreCase("zh-hk")) {
            return ZH_HK_LOCALE;
        }
        return Locale.ENGLISH;
    }
}