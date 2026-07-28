package com.bct.ngtpa.apiservice.application.dto.message;

import java.time.Instant;

/**
 * Passive framework-free DTO for a future delivery-task handoff boundary.
 *
 * <p>
 * This model intentionally has no queueing, persistence, retry, worker, or
 * provider-payload behavior.
 * </p>
 */
public record MessageDeliveryTask(
                String taskId,
                MessageDeliveryCommand command,
                Instant createdAt,
                String correlationId) {
}
