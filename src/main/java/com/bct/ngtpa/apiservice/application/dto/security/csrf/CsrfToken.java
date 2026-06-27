package com.bct.ngtpa.apiservice.application.dto.security.csrf;

import java.time.Instant;

public record CsrfToken(
        String workflowRef,
        String tokenHash,
        Instant createdAt,
        Instant expiresAt) {
}
