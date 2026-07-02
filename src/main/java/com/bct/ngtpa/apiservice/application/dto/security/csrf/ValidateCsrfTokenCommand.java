package com.bct.ngtpa.apiservice.application.dto.security.csrf;

import java.time.Instant;

public record ValidateCsrfTokenCommand(
        String workflowRef,
        String submittedToken,
        String storedTokenHash,
        Instant now,
        Instant expiresAt,
        String origin,
        String referer,
        String correlationId) {
}
