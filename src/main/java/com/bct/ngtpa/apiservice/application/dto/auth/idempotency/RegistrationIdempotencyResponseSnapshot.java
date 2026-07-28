package com.bct.ngtpa.apiservice.application.dto.auth.idempotency;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public record RegistrationIdempotencyResponseSnapshot(
        String status,
        String serializedBody,
        Map<String, String> safeHeaders) {
    private static final Set<String> BLOCKED_HEADERS = Set.of(
            "set-cookie",
            "cookie",
            "authorization",
            "xsrf-token",
            "x-xsrf-token");

    public RegistrationIdempotencyResponseSnapshot {
        Objects.requireNonNull(status, "status must not be null");
        if (status.isBlank()) {
            throw new IllegalArgumentException("status must not be blank");
        }
        safeHeaders = copyAndValidateHeaders(safeHeaders);
    }

    private static Map<String, String> copyAndValidateHeaders(Map<String, String> headers) {
        if (headers == null || headers.isEmpty()) {
            return Map.of();
        }
        Map<String, String> copy = new LinkedHashMap<>();
        for (var entry : headers.entrySet()) {
            String name = entry.getKey();
            String value = entry.getValue();
            if (name == null || name.isBlank() || unsafeHeaderName(name) || unsafeHeaderValue(value)) {
                throw new IllegalArgumentException("Unsafe idempotency snapshot header");
            }
            copy.put(name, value);
        }
        return Map.copyOf(copy);
    }

    private static boolean unsafeHeaderName(String name) {
        return BLOCKED_HEADERS.contains(name.trim().toLowerCase(Locale.ROOT));
    }

    private static boolean unsafeHeaderValue(String value) {
        if (value == null) {
            return false;
        }
        String lower = value.toLowerCase(Locale.ROOT);
        return lower.contains("csrf_")
                || lower.contains("auth_flow_sid")
                || lower.contains("bearer");
    }

    @Override
    public String toString() {
        return "RegistrationIdempotencyResponseSnapshot[status=" + status + ", safeHeaderCount=" + safeHeaders.size()
                + "]";
    }
}
