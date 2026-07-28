package com.bct.ngtpa.apiservice.application.dto.security.csrf;

import java.time.Instant;

public record CreateCsrfTokenCommand(
                String workflowRef,
                Instant now,
                int ttlSeconds,
                String correlationId) {
}
