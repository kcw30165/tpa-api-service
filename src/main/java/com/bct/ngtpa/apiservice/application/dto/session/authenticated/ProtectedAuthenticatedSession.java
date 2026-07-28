package com.bct.ngtpa.apiservice.application.dto.session.authenticated;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Hybrid Redis wire envelope for an authenticated portal session.
 *
 * <p>Lifecycle metadata is readable for operations/support. Sensitive actor,
 * account, and CSRF state remains encrypted under {@code data.attributes}.
 * The protector authenticates all readable metadata as AES-GCM AAD.</p>
 */
public record ProtectedAuthenticatedSession(
        String authenticatedSessionId,
        AuthenticatedSessionStatus status,
        Data data,
        Instant createdAt,
        Instant updatedAt,
        Instant idleExpiresAt,
        Instant absoluteExpiresAt) {

    public static final String CIPHERTEXT = "authenticatedContext.ciphertext";
    public static final String ALGORITHM = "authenticatedContext.algorithm";
    public static final String NONCE = "authenticatedContext.nonce";
    public static final String AUTHENTICATION_TAG = "authenticatedContext.authenticationTag";
    public static final String KEY_VERSION = "authenticatedContext.keyVersion";

    public ProtectedAuthenticatedSession {
        authenticatedSessionId = required(authenticatedSessionId, "authenticatedSessionId");
        if (status == null) {
            throw new IllegalArgumentException("status must not be null");
        }
        data = data == null ? new Data(Map.of()) : data;
        if (createdAt == null || updatedAt == null || idleExpiresAt == null || absoluteExpiresAt == null) {
            throw new IllegalArgumentException("authenticated session lifecycle timestamps are required");
        }
        if (updatedAt.isBefore(createdAt)
                || idleExpiresAt.isBefore(updatedAt)
                || absoluteExpiresAt.isBefore(createdAt)) {
            throw new IllegalArgumentException("invalid authenticated session lifecycle metadata");
        }
        requiredAttribute(CIPHERTEXT, data.attributes());
        requiredAttribute(ALGORITHM, data.attributes());
        requiredAttribute(NONCE, data.attributes());
        requiredAttribute(AUTHENTICATION_TAG, data.attributes());
        requiredAttribute(KEY_VERSION, data.attributes());
    }

    public String ciphertext() {
        return data.attributes().get(CIPHERTEXT);
    }

    public String algorithm() {
        return data.attributes().get(ALGORITHM);
    }

    public String nonce() {
        return data.attributes().get(NONCE);
    }

    public String authenticationTag() {
        return data.attributes().get(AUTHENTICATION_TAG);
    }

    public String keyVersion() {
        return data.attributes().get(KEY_VERSION);
    }

    private static String required(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value.trim();
    }

    private static void requiredAttribute(String name, Map<String, String> attributes) {
        required(attributes.get(name), name);
    }

    @Override
    public String toString() {
        return "ProtectedAuthenticatedSession[authenticatedSessionId=<redacted>, status=" + status
                + ", data=<protected>, createdAt=" + createdAt
                + ", updatedAt=" + updatedAt
                + ", idleExpiresAt=" + idleExpiresAt
                + ", absoluteExpiresAt=" + absoluteExpiresAt + "]";
    }

    public record Data(Map<String, String> attributes) {
        public Data {
            Map<String, String> copy = new LinkedHashMap<>();
            if (attributes != null) {
                attributes.forEach((key, value) -> copy.put(required(key, "attribute name"), value));
            }
            attributes = Map.copyOf(copy);
        }
    }
}
