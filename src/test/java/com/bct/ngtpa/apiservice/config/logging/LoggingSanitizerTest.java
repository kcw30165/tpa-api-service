package com.bct.ngtpa.apiservice.config.logging;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class LoggingSanitizerTest {

    private final LoggingSanitizer sanitizer = new LoggingSanitizer(new ObjectMapper(), properties());

    @Test
    void masksSensitiveMapKeys() {
        Object sanitized = sanitizer.sanitizeValue(Map.of(
                "authorization", "Bearer abc",
                "traceId", "trace-1",
                "policy-no", "P-100"));

        Map<?, ?> sanitizedMap = assertInstanceOf(Map.class, sanitized);
        assertEquals("***", sanitizedMap.get("authorization"));
        assertEquals("***", sanitizedMap.get("policy-no"));
        assertEquals("trace-1", sanitizedMap.get("traceId"));
    }

    @Test
    void masksSensitiveRecordFieldNames() {
        Object sanitized = sanitizer.sanitizeValue(new MemberPayload("user-1", "Alice", "secret-key"));

        Map<?, ?> sanitizedMap = assertInstanceOf(Map.class, sanitized);
        assertEquals("***", sanitizedMap.get("userId"));
        assertEquals("Alice", sanitizedMap.get("displayName"));
        assertEquals("***", sanitizedMap.get("apiKey"));
    }

    @Test
    void leavesNormalFieldsUnmasked() {
        Object sanitized = sanitizer.sanitizeValue(new AuditPayload("trace-1", "DEV"));

        Map<?, ?> sanitizedMap = assertInstanceOf(Map.class, sanitized);
        assertEquals("trace-1", sanitizedMap.get("traceId"));
        assertEquals("DEV", sanitizedMap.get("environment"));
    }

    private record MemberPayload(String userId, String displayName, String apiKey) {
    }

    private record AuditPayload(String traceId, String environment) {
    }

    private static LoggingSanitizerProperties properties() {
        LoggingSanitizerProperties properties = new LoggingSanitizerProperties();
        properties.setSensitiveTokens(List.of(
                "authorization",
                "token",
                "secret",
                "password",
                "apiKey",
                "Certificate",
                "clientSecret",
                "policyNo",
                "certNo",
                "userId",
                "privateKey",
                "publicKey",
                "policy-no",
                "cert-no",
                "user-id"));
        return properties;
    }
}