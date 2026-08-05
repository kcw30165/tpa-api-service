package com.bct.ngtpa.apiservice.application.dto.session.authenticated;
import java.time.Duration;
import java.util.Objects;
public record RotateAuthenticatedSessionCsrfHashCommand(
        AuthenticatedSessionId authenticatedSessionId,
        ProtectedAuthenticatedSession expectedProtectedSession,
        ProtectedAuthenticatedSession replacementProtectedSession,
        String expectedCsrfTokenHash,
        String replacementCsrfTokenHash,
        Duration ttl,
        String correlationId) {
    public RotateAuthenticatedSessionCsrfHashCommand {
        Objects.requireNonNull(authenticatedSessionId);
        Objects.requireNonNull(expectedProtectedSession);
        Objects.requireNonNull(replacementProtectedSession);
        required(expectedCsrfTokenHash, "expectedCsrfTokenHash");
        required(replacementCsrfTokenHash, "replacementCsrfTokenHash");
        if (expectedCsrfTokenHash.equals(replacementCsrfTokenHash))
            throw new IllegalArgumentException("replacement CSRF hash must differ");
        if (ttl == null || ttl.isZero() || ttl.isNegative())
            throw new IllegalArgumentException("ttl must be positive");
        correlationId = correlationId == null || correlationId.isBlank() ? null : correlationId.trim();
    }
    private static void required(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " required");
    }
    @Override public String toString() { return "RotateAuthenticatedSessionCsrfHashCommand[<redacted>]"; }
}
