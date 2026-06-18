package com.bct.ngtpa.apiservice.shared.web;

import java.util.Locale;

public record RequestHeaderContext(
        String accountRef,
        String requestId,
        String language,
        String sessionId) {

    public RequestHeaderContext(String accountRef, String requestId, String language) {
        this(accountRef, requestId, language, null);
    }

    public RequestHeaderContext {
        accountRef = trimToNull(accountRef);
        requestId = trimToNull(requestId);
        language = normalizeLanguage(language);
        sessionId = trimToNull(sessionId);
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String normalizeLanguage(String value) {
        String trimmed = trimToNull(value);
        if (trimmed == null) {
            return "en";
        }
        String normalized = trimmed.replace('_', '-').toLowerCase(Locale.ROOT);
        if (normalized.startsWith("zh")) {
            return "zh-HK";
        }
        return "en";
    }
}
