package com.bct.ngtpa.apiservice.application.dto.auth.forgotpassword.idempotency;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public record ForgotPasswordIdempotencyReplaySnapshot(
        String outcomeCode,
        String opaqueCommitRef,
        Map<String, String> safeHeaders,
        String identityType) {
    private static final Set<String> BLOCKED_HEADERS = Set.of(
            "set-cookie",
            "cookie",
            "authorization",
            "xsrf-token",
            "x-xsrf-token");

    public ForgotPasswordIdempotencyReplaySnapshot {
        Objects.requireNonNull(outcomeCode, "outcomeCode must not be null");
        if (outcomeCode.isBlank()) {
            throw new IllegalArgumentException("outcomeCode must not be blank");
        }
        opaqueCommitRef = requireText(opaqueCommitRef, "opaqueCommitRef");
        safeHeaders = sanitizeHeaders(safeHeaders);
        identityType = identityType == null || identityType.isBlank() ? null : identityType.trim();
    }

    public ForgotPasswordIdempotencyReplaySnapshot(
            String outcomeCode,
            String opaqueCommitRef,
            Map<String, String> safeHeaders) {
        this(outcomeCode, opaqueCommitRef, safeHeaders, null);
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }

    private static Map<String, String> sanitizeHeaders(Map<String, String> headers) {
        if (headers == null || headers.isEmpty()) {
            return Map.of();
        }
        Map<String, String> copy = new LinkedHashMap<>();
        for (var entry : headers.entrySet()) {
            String name = entry.getKey();
            String value = entry.getValue();
            if (name == null || name.isBlank()) {
                throw new IllegalArgumentException("Unsafe idempotency snapshot header");
            }
            if (BLOCKED_HEADERS.contains(name.trim().toLowerCase(Locale.ROOT))) {
                throw new IllegalArgumentException("Unsafe idempotency snapshot header");
            }
            if (value != null) {
                String lowerValue = value.toLowerCase(Locale.ROOT);
                if (lowerValue.contains("csrf_") || lowerValue.contains("auth_flow_sid")
                        || lowerValue.contains("bearer")) {
                    throw new IllegalArgumentException("Unsafe idempotency snapshot header");
                }
            }
            copy.put(name, value);
        }
        return Map.copyOf(copy);
    }

    @Override
    public String toString() {
        return "ForgotPasswordIdempotencyReplaySnapshot[outcomeCode=" + outcomeCode
                + ", opaqueCommitRef=<redacted>, safeHeaderCount=" + safeHeaders.size()
                + ", identityType=" + (identityType == null ? "null" : "<redacted>") + "]";
    }
}