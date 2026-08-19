package com.bct.ngtpa.apiservice.adapter.out.security.audit;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.application.dto.security.audit.SecurityAuditEvent;
import com.bct.ngtpa.apiservice.application.dto.security.audit.SecurityAuditEventOutcome;
import com.bct.ngtpa.apiservice.application.dto.security.audit.SecurityAuditEventSeverity;
import com.bct.ngtpa.apiservice.application.dto.security.audit.SecurityAuditEventType;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import reactor.test.StepVerifier;

@ExtendWith(OutputCaptureExtension.class)
class LoggingSecurityAuditEventAdapterTest {

  private final LoggingSecurityAuditEventAdapter adapter = new LoggingSecurityAuditEventAdapter();

  @Test
  void sanitizerRedactsForbiddenMetadataKeys() {
    Map<String, String> metadata = new LinkedHashMap<>();
    metadata.put("password", "secret");
    metadata.put("csrfToken", "csrf_abc");
    metadata.put("cookie", "reg_sid=reg_xxx");
    metadata.put("mobile", "91234567");
    metadata.put("email", "member@example.com");
    metadata.put("smsContent", "Your code is 123456");

    Map<String, String> sanitized = new SecurityAuditMetadataSanitizer().sanitize(metadata);

    assertThat(sanitized.values())
        .doesNotContain(
            "secret",
            "csrf_abc",
            "reg_sid=reg_xxx",
            "91234567",
            "member@example.com",
            "Your code is 123456");
    assertThat(sanitized.values()).allMatch("[REDACTED]"::equals);
  }

  @Test
  void sanitizerPreservesSafeMetadataKeys() {
    Map<String, String> metadata = new LinkedHashMap<>();
    metadata.put("rateLimitBucket", "OTP_SEND_BY_DESTINATION_HASH");
    metadata.put("status", "BLOCKED");
    metadata.put("retryAfterSeconds", "300");
    metadata.put("originAllowed", "false");
    metadata.put("refererPresent", "true");

    Map<String, String> sanitized = new SecurityAuditMetadataSanitizer().sanitize(metadata);

    assertThat(sanitized).containsAllEntriesOf(metadata);
  }

  @Test
  void loggingAdapterDoesNotLogOtpTokenCookiePhoneEmailOrContent(CapturedOutput output) {
    Map<String, String> metadata = new LinkedHashMap<>();
    metadata.put("otpCode", "123456");
    metadata.put("csrfToken", "csrf_abc");
    metadata.put("cookie", "reg_sid=reg_xxx");
    metadata.put("mobile", "91234567");
    metadata.put("email", "member@example.com");
    metadata.put("smsContent", "Your code is 123456");
    metadata.put("password", "secret");

    StepVerifier.create(adapter.publish(sampleEvent(metadata))).verifyComplete();

    assertThat(output.getOut())
        .doesNotContain("123456")
        .doesNotContain("csrf_abc")
        .doesNotContain("reg_sid")
        .doesNotContain("reg_xxx")
        .doesNotContain("91234567")
        .doesNotContain("member@example.com")
        .doesNotContain("Your code is 123456")
        .doesNotContain("secret");
  }

  @Test
  void loggingAdapterLogsStructuredSafeEventFields(CapturedOutput output) {
    Map<String, String> metadata = new LinkedHashMap<>();
    metadata.put("retryAfterSeconds", "300");

    StepVerifier.create(adapter.publish(sampleEvent(metadata))).verifyComplete();

    assertThat(output.getOut())
        .contains("SECURITY_AUDIT")
        .contains("RATE_LIMIT_BLOCKED")
        .contains("BLOCKED")
        .contains("REGISTRATION")
        .contains("OTP_RESEND")
        .contains("retryAfterSeconds");
  }

  @Test
  void loggingAdapterHandlesNullMetadataSafely(CapturedOutput output) {
    StepVerifier.create(adapter.publish(sampleEvent(null))).verifyComplete();

    assertThat(output.getOut()).contains("SECURITY_AUDIT");
  }

  private SecurityAuditEvent sampleEvent(Map<String, String> metadata) {
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
        Instant.parse("2026-01-01T00:00:00Z"));
  }
}
