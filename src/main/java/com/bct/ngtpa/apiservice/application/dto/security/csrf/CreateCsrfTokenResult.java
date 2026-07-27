package com.bct.ngtpa.apiservice.application.dto.security.csrf;

import java.time.Instant;

public record CreateCsrfTokenResult(
                String token,
                String tokenHash,
                Instant createdAt,
                Instant expiresAt,
                int expiresInSeconds) {
}
