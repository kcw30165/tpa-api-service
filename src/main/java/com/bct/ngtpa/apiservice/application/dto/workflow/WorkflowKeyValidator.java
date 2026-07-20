package com.bct.ngtpa.apiservice.application.dto.workflow;

import java.util.Locale;
import java.util.regex.Pattern;

final class WorkflowKeyValidator {
    private static final Pattern CODE_LIKE = Pattern.compile("^[a-z][A-Za-z0-9]*(\\.[a-z][A-Za-z0-9]*)+$");
    private static final Pattern LONG_DIGITS = Pattern.compile(".*\\d{8,}.*");

    private WorkflowKeyValidator() {
    }

    static String validate(String value, String safeErrorMessage) {
        if (value == null) {
            throw new IllegalArgumentException(safeErrorMessage);
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty() || !CODE_LIKE.matcher(trimmed).matches() || containsUnsafeToken(trimmed)) {
            throw new IllegalArgumentException(safeErrorMessage);
        }
        return trimmed;
    }

    private static boolean containsUnsafeToken(String value) {
        String lower = value.toLowerCase(Locale.ROOT);
        return value.chars().anyMatch(Character::isWhitespace)
                || value.contains("@")
                || LONG_DIGITS.matcher(value).matches()
                || lower.contains("csrf_")
                || lower.contains("otp_")
                || isUnsafePasswordLikeKey(lower)
                || lower.contains("bearer")
                || lower.contains("cookie");
    }

    private static boolean isUnsafePasswordLikeKey(String lower) {
        return "password".equals(lower)
                || lower.startsWith("password.")
                || lower.endsWith(".password")
                || (lower.contains(".password.")
                        && !lower.startsWith("registration.password.")
                        && !lower.startsWith("err.registration.password."));
    }
}
