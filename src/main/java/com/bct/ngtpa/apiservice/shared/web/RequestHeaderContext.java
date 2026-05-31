package com.bct.ngtpa.apiservice.shared.web;

public record RequestHeaderContext(
        String accountRef,
        String requestId,
        String language) {
        private static final String DEFAULT_LANGUAGE = "en";

        public RequestHeaderContext {
                accountRef = trimToNull(accountRef);
                requestId = trimToNull(requestId);
                language = normalizeLanguage(language);
        }

        private static String normalizeLanguage(String value) {
                String normalized = trimToNull(value);
                if (normalized == null) {
                        return DEFAULT_LANGUAGE;
                }

                String primary = normalized.split(",", 2)[0].trim();
                int qualitySeparator = primary.indexOf(';');
                if (qualitySeparator >= 0) {
                        primary = primary.substring(0, qualitySeparator).trim();
                }

                if (primary.isEmpty()) {
                        return DEFAULT_LANGUAGE;
                }

                String tag = primary.replace('_', '-');
                if ("en".equalsIgnoreCase(tag)) {
                        return DEFAULT_LANGUAGE;
                }
                if ("zh-hk".equalsIgnoreCase(tag)) {
                        return "zh-HK";
                }
                return tag;
        }

        private static String trimToNull(String value) {
                if (value == null) {
                        return null;
                }
                String trimmed = value.trim();
                return trimmed.isEmpty() ? null : trimmed;
        }
}