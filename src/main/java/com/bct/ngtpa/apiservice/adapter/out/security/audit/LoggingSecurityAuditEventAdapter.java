package com.bct.ngtpa.apiservice.adapter.out.security.audit;

import com.bct.ngtpa.apiservice.application.dto.security.audit.SecurityAuditEvent;
import com.bct.ngtpa.apiservice.application.port.out.SecurityAuditEventPort;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@Slf4j
public class LoggingSecurityAuditEventAdapter implements SecurityAuditEventPort {

  private static final String EVENT_CATEGORY = "SECURITY_AUDIT";
  private final SecurityAuditMetadataSanitizer metadataSanitizer;

  public LoggingSecurityAuditEventAdapter() {
    this(new SecurityAuditMetadataSanitizer());
  }

  LoggingSecurityAuditEventAdapter(SecurityAuditMetadataSanitizer metadataSanitizer) {
    this.metadataSanitizer = metadataSanitizer;
  }

  @Override
  public Mono<Void> publish(SecurityAuditEvent event) {
    return Mono.fromRunnable(() -> log.info("{}", toLogEvent(event))).then();
  }

  private Map<String, Object> toLogEvent(SecurityAuditEvent event) {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("eventCategory", EVENT_CATEGORY);
    if (event == null) {
      payload.put("eventType", "UNKNOWN");
      payload.put("safeMetadata", Map.of());
      return payload;
    }
    payload.put("eventType", event.type());
    payload.put("outcome", event.outcome());
    payload.put("severity", event.severity());
    payload.put("scenario", event.scenario());
    payload.put("action", event.action());
    payload.put("reasonCode", event.reasonCode());
    payload.put("correlationId", event.correlationId());
    payload.put("subjectBucketId", event.subjectBucketId());
    payload.put("clientBucketId", event.clientBucketId());
    payload.put("destinationBucketId", event.destinationBucketId());
    payload.put("safeMetadata", metadataSanitizer.sanitize(event.safeMetadata()));
    payload.put("occurredAt", event.occurredAt());
    return payload;
  }
}
