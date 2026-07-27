package com.bct.ngtpa.apiservice.adapter.out.security.audit;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

class SecurityAuditMetadataSanitizer {

    private static final String REDACTED = "[REDACTED]";
    private static final Set<String> FORBIDDEN_KEYS = Set.of(
            "password", "token", "csrftoken", "x-csrf-token", "authorization", "cookie", "reg_sid",
            "session", "sessionid", "registrationsessionid", "otp", "otpcode", "code", "rawcode",
            "mobile", "phone", "email", "recipient", "destination", "hkid", "passport", "policyno",
            "policy-no", "certno", "cert-no", "username", "smscontent", "emailbody", "body");
    private static final Pattern EMAIL_VALUE = Pattern.compile(".+@.+\\..+");
    private static final Pattern PHONE_LIKE_VALUE = Pattern.compile(".*\\d{8,}.*");
    private static final Pattern BEARER_VALUE = Pattern.compile("(?i).*bearer\\s+[a-z0-9._\\-]+.*");

    Map<String, String> sanitize(Map<String, String> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return Map.of();
        }
        Map<String, String> sanitized = new LinkedHashMap<>();
        metadata.forEach((key, value) -> sanitized.put(key, sanitizeValue(key, value)));
        return Map.copyOf(sanitized);
    }

    private String sanitizeValue(String key, String value) {
        if (isForbiddenKey(key) || containsForbiddenValue(key, value)) {
            return REDACTED;
        }
        return value;
    }

    private boolean isForbiddenKey(String key) {
        if (key == null || key.isBlank()) {
            return true;
        }
        String normalized = key.replace("_", "").replace("-", "").toLowerCase(Locale.ROOT);
        String lowercase = key.toLowerCase(Locale.ROOT);
        return FORBIDDEN_KEYS.contains(lowercase) || FORBIDDEN_KEYS.contains(normalized);
    }

    private boolean containsForbiddenValue(String key, String value) {
        if (value == null) {
            return false;
        }
        String lower = value.toLowerCase(Locale.ROOT);
        return EMAIL_VALUE.matcher(value).matches()
                || PHONE_LIKE_VALUE.matcher(value).matches()
                || lower.startsWith("csrf_")
                || lower.startsWith("reg_")
                || isSensitiveOtpValue(key, value)
                || BEARER_VALUE.matcher(value).matches();
    }

    private boolean isSensitiveOtpValue(String key, String value) {
        if (value == null) {
            return false;
        }
        String lower = value.toLowerCase(Locale.ROOT);
        if (!lower.startsWith("otp_")) {
            return false;
        }
        // Preserve safe enum-like metadata values, e.g.
        // rateLimitBucket=OTP_SEND_BY_DESTINATION_HASH.
        // Raw OTP challenge identifiers should be redacted through forbidden keys such
        // as otp/otpCode/code/rawCode,
        // or by non-enum-looking lowercase token values such as otp_abc123.
        return !value.matches("^[A-Z0-9_]+$");
    }
}
