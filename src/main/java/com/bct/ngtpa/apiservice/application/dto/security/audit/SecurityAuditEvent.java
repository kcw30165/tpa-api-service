package com.bct.ngtpa.apiservice.application.dto.security.audit;

import java.time.Instant;
import java.util.Map;

public record SecurityAuditEvent(
    SecurityAuditEventType type,
    SecurityAuditEventOutcome outcome,
    SecurityAuditEventSeverity severity,
    String scenario,
    String action,
    String reasonCode,
    String correlationId,
    String subjectBucketId,
    String clientBucketId,
    String destinationBucketId,
    Map<String, String> safeMetadata,
    Instant occurredAt) {

  public SecurityAuditEvent {
    safeMetadata = safeMetadata == null ? Map.of() : Map.copyOf(safeMetadata);
  }
}
