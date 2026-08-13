package com.bct.ngtpa.apiservice.application.port.out;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Opaque Username Recovery idempotency key value object.
 *
 * <p>Design references: {@code docs/design/6-username-recovery/username-recovery-api-contract-design.md}
 * section 4 (Idempotency-Key header on start/request/cancel) and
 * {@code docs/design/6-username-recovery/username-recovery-session-redis-design.md} section 8
 * (safe persistence; raw session/CSRF values are never stored). Mirrors
 * {@code RegistrationIdempotencyKey} safety rules: safe opaque values only, unsafe tokens
 * rejected without echoing, and {@link #toString()} never discloses the key.
 */
public record UsernameRecoveryIdempotencyKey(String value) {

    private static final Pattern SAFE = Pattern.compile("^[A-Za-z0-9_-]{8,128}$");
    private static final Pattern LONG_DIGITS = Pattern.compile(".*\\d{8,}.*");

    public UsernameRecoveryIdempotencyKey {
        if (value == null || value.isBlank() || !SAFE.matcher(value).matches()
                || containsUnsafeToken(value)) {
            throw new IllegalArgumentException("Invalid username recovery idempotency key");
        }
    }

    private static boolean containsUnsafeToken(String value) {
        String lower = value.toLowerCase(Locale.ROOT);
        return value.chars().anyMatch(Character::isWhitespace)
                || value.contains(":")
                || value.contains("/")
                || value.contains("@")
                || LONG_DIGITS.matcher(value).matches() && value.chars().allMatch(Character::isDigit)
                || lower.contains("csrf_")
                || lower.contains("otp_")
                || lower.contains("auth_flow_sid")
                || lower.contains("bearer")
                || lower.contains("cookie");
    }

    @Override
    public String toString() {
        return "UsernameRecoveryIdempotencyKey[redacted]";
    }
}
