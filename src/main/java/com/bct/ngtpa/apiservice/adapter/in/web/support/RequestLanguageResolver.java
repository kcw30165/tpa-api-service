package com.bct.ngtpa.apiservice.adapter.in.web.support;

import com.bct.ngtpa.apiservice.shared.web.RequestHeaderContext;

public final class RequestLanguageResolver {

    private static final String DEFAULT_LANGUAGE = "en";
    private static final String ZH_HK_LANGUAGE = "zh_HK";

    private RequestLanguageResolver() {
    }

    public static String resolve(
            RequestHeaderContext requestHeaderContext,
            String rawAcceptLanguage,
            String fallbackLang) {
        if (hasText(rawAcceptLanguage)) {
            if (requestHeaderContext != null && hasText(requestHeaderContext.language())) {
                return normalize(requestHeaderContext.language());
            }
            return normalize(rawAcceptLanguage);
        }

        return normalize(fallbackLang);
    }

    public static boolean isZhHk(String language) {
        return ZH_HK_LANGUAGE.equalsIgnoreCase(normalize(language));
    }

    public static String normalize(String language) {
        String normalized = trimToNull(language);
        if (normalized == null) {
            return DEFAULT_LANGUAGE;
        }

        String tag = normalized.replace('_', '-');
        if (tag.equalsIgnoreCase("en")
                || tag.regionMatches(true, 0, "en-", 0, 3)) {
            return DEFAULT_LANGUAGE;
        }
        if (tag.equalsIgnoreCase("zh")
                || tag.equalsIgnoreCase("zh-HK")) {
            return ZH_HK_LANGUAGE;
        }

        return DEFAULT_LANGUAGE;
    }

    private static boolean hasText(String value) {
        return trimToNull(value) != null;
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}