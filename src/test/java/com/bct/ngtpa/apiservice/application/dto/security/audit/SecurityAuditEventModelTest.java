package com.bct.ngtpa.apiservice.application.dto.security.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.lang.reflect.RecordComponent;
import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class SecurityAuditEventModelTest {

  @Test
  void securityAuditEventTypeDefinesExpectedSecurityEvents() {
    assertThat(Set.copyOf(Arrays.asList(SecurityAuditEventType.values())))
        .containsExactlyInAnyOrder(
            SecurityAuditEventType.RATE_LIMIT_BLOCKED,
            SecurityAuditEventType.CSRF_VALIDATION_FAILED,
            SecurityAuditEventType.ORIGIN_REFERER_REJECTED,
            SecurityAuditEventType.OTP_CHALLENGE_LOCKED,
            SecurityAuditEventType.OTP_VERIFICATION_FAILED,
            SecurityAuditEventType.MESSAGE_DELIVERY_FAILED,
            SecurityAuditEventType.REGISTRATION_SECURITY_REJECTED);
  }

  @Test
  void securityAuditEventOutcomeDefinesExpectedValues() {
    assertThat(Set.copyOf(Arrays.asList(SecurityAuditEventOutcome.values())))
        .containsExactlyInAnyOrder(
            SecurityAuditEventOutcome.ACCEPTED,
            SecurityAuditEventOutcome.REJECTED,
            SecurityAuditEventOutcome.FAILED,
            SecurityAuditEventOutcome.BLOCKED);
  }

  @Test
  void securityAuditEventSeverityDefinesExpectedValues() {
    assertThat(Set.copyOf(Arrays.asList(SecurityAuditEventSeverity.values())))
        .containsExactlyInAnyOrder(
            SecurityAuditEventSeverity.INFO,
            SecurityAuditEventSeverity.WARN,
            SecurityAuditEventSeverity.ERROR);
  }

  @Test
  void securityAuditEventCarriesOnlySafeStructuredFields() {
    Instant occurredAt = Instant.parse("2026-01-01T00:00:00Z");
    var event = sampleEvent(Map.of("retryAfterSeconds", "300"), occurredAt);

    assertThat(event.type()).isEqualTo(SecurityAuditEventType.RATE_LIMIT_BLOCKED);
    assertThat(event.outcome()).isEqualTo(SecurityAuditEventOutcome.BLOCKED);
    assertThat(event.severity()).isEqualTo(SecurityAuditEventSeverity.WARN);
    assertThat(event.scenario()).isEqualTo("REGISTRATION");
    assertThat(event.action()).isEqualTo("OTP_RESEND");
    assertThat(event.reasonCode()).isEqualTo("RATE_LIMIT_BLOCKED");
    assertThat(event.correlationId()).isEqualTo("request-123");
    assertThat(event.subjectBucketId()).isEqualTo("subj_opaque");
    assertThat(event.clientBucketId()).isEqualTo("client_opaque");
    assertThat(event.destinationBucketId()).isEqualTo("dest_EMAIL_hash");
    assertThat(event.safeMetadata()).containsEntry("retryAfterSeconds", "300");
    assertThat(event.occurredAt()).isEqualTo(occurredAt);
  }

  @Test
  void securityAuditEventDoesNotExposeRawSensitiveFields() {
    Set<String> componentNames =
        Arrays.stream(SecurityAuditEvent.class.getRecordComponents())
            .map(RecordComponent::getName)
            .collect(Collectors.toSet());
    Set<String> accessorNames =
        Arrays.stream(SecurityAuditEvent.class.getDeclaredMethods())
            .filter(method -> method.getParameterCount() == 0)
            .map(method -> method.getName())
            .collect(Collectors.toSet());
    Set<String> forbiddenNames =
        Set.of(
            "email",
            "mobile",
            "phone",
            "otp",
            "otpCode",
            "rawCode",
            "csrfToken",
            "cookie",
            "regSid",
            "registrationSessionId",
            "password",
            "username",
            "requestBody",
            "smsContent",
            "emailBody",
            "authorization");

    forbiddenNames.forEach(
        name -> {
          assertThat(componentNames).doesNotContain(name);
          assertThat(accessorNames).doesNotContain(name);
        });
  }

  @Test
  void safeMetadataIsDefensivelyCopied() {
    var nullMetadataEvent = sampleEvent(null, Instant.parse("2026-01-01T00:00:00Z"));
    assertThat(nullMetadataEvent.safeMetadata()).isEmpty();

    Map<String, String> metadata = new LinkedHashMap<>();
    metadata.put("status", "BLOCKED");
    var event = sampleEvent(metadata, Instant.parse("2026-01-01T00:00:00Z"));
    metadata.put("status", "MUTATED");

    assertThat(event.safeMetadata()).containsEntry("status", "BLOCKED");
    assertThrows(UnsupportedOperationException.class, () -> event.safeMetadata().put("x", "y"));
  }

  private SecurityAuditEvent sampleEvent(Map<String, String> metadata, Instant occurredAt) {
    return new SecurityAuditEvent(
        SecurityAuditEventType.RATE_LIMIT_BLOCKED,
        SecurityAuditEventOutcome.BLOCKED,
        SecurityAuditEventSeverity.WARN,
        "REGISTRATION",
        "OTP_RESEND",
        "RATE_LIMIT_BLOCKED",
        "request-123",
        "subj_opaque",
        "client_opaque",
        "dest_EMAIL_hash",
        metadata,
        occurredAt);
  }
}
