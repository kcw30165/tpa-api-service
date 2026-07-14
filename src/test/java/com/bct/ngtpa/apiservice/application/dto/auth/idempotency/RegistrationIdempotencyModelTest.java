package com.bct.ngtpa.apiservice.application.dto.auth.idempotency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RegistrationIdempotencyModelTest {
    private static final String HASH = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";
    private static final Instant NOW = Instant.parse("2026-07-03T00:00:00Z");

    @Test
    void registrationIdempotencyActionDefinesRequiredActions() {
        assertThat(RegistrationIdempotencyAction.values()).containsExactly(
                RegistrationIdempotencyAction.REGISTRATION_START,
                RegistrationIdempotencyAction.OTP_RESEND,
                RegistrationIdempotencyAction.OTP_VERIFY,
                RegistrationIdempotencyAction.REGISTRATION_COMPLETE);
        assertThat(RegistrationIdempotencyAction.REGISTRATION_START.keyNamespace()).isEqualTo("registration-start");
        assertThat(RegistrationIdempotencyAction.OTP_RESEND.keyNamespace()).isEqualTo("otp-resend");
        assertThat(RegistrationIdempotencyAction.OTP_VERIFY.keyNamespace()).isEqualTo("otp-verify");
        assertThat(RegistrationIdempotencyAction.REGISTRATION_COMPLETE.keyNamespace())
                .isEqualTo("registration-complete");
    }

    @Test
    void registrationIdempotencyKeyAcceptsSafeOpaqueValues() {
        assertThat(new RegistrationIdempotencyKey("idem_12345678").value()).isEqualTo("idem_12345678");
        assertThat(new RegistrationIdempotencyKey("550e8400-e29b-41d4-a716-446655440000").value())
                .isEqualTo("550e8400-e29b-41d4-a716-446655440000");
        assertThat(new RegistrationIdempotencyKey("abcDEF123_-").value()).isEqualTo("abcDEF123_-");
    }

    @Test
    void registrationIdempotencyKeyRejectsUnsafeValuesWithoutEchoing() {
        List<String> unsafe = List.of("", "   ", "abc def", "abc:def", "abc/def", "member@example.com", "91234567",
                "csrf_abc", "otp_abc", "auth_flow_sid=afs_secret", "Bearer abc", "Cookie abc");
        for (String value : unsafe) {
            var assertion = assertThatThrownBy(() -> new RegistrationIdempotencyKey(value))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Invalid registration idempotency key");
            if (!value.isEmpty()) {
                assertion.hasMessageNotContaining(value);
            }
        }
    }

    @Test
    void registrationRequestHashRequiresSha256Hex() {
        assertThat(new RegistrationRequestHash(HASH).value()).isEqualTo(HASH);
        for (String value : List.of("", "not-hex", "abc", "member@example.com", "csrf_abc")) {
            var assertion = assertThatThrownBy(() -> new RegistrationRequestHash(value))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Invalid registration request hash");
            if (!value.isEmpty()) {
                assertion.hasMessageNotContaining(value);
            }
        }
    }

    @Test
    void idempotencyRecordRequiresCompletedSnapshotWhenCompleted() {
        assertThatThrownBy(() -> new RegistrationIdempotencyRecord(
                RegistrationIdempotencyAction.REGISTRATION_START,
                new RegistrationIdempotencyKey("idem_12345678"),
                new RegistrationRequestHash(HASH),
                RegistrationIdempotencyStatus.COMPLETED,
                null,
                NOW,
                NOW,
                NOW.plusSeconds(300)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("completed idempotency record requires response snapshot");

        var processing = record(RegistrationIdempotencyStatus.PROCESSING, null);
        assertThat(processing.responseSnapshot()).isNull();
    }

    @Test
    void idempotencyRecordRejectsInvalidExpiry() {
        assertThatThrownBy(() -> new RegistrationIdempotencyRecord(
                RegistrationIdempotencyAction.REGISTRATION_START,
                new RegistrationIdempotencyKey("idem_12345678"),
                new RegistrationRequestHash(HASH),
                RegistrationIdempotencyStatus.PROCESSING,
                null,
                NOW,
                NOW,
                NOW))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("expiresAt must be after createdAt");
    }

    @Test
    void responseSnapshotDefensivelyCopiesHeaders() {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("X-Safe", "value");
        var snapshot = new RegistrationIdempotencyResponseSnapshot("200", "{\"ok\":true}", headers);
        headers.put("X-Other", "mutated");

        assertThat(snapshot.safeHeaders()).containsExactly(Map.entry("X-Safe", "value"));
        assertThatThrownBy(() -> snapshot.safeHeaders().put("X-New", "value"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void responseSnapshotRejectsUnsafeHeaders() {
        for (String header : List.of("Set-Cookie", "Cookie", "Authorization", "XSRF-TOKEN", "X-XSRF-TOKEN")) {
            assertThatThrownBy(() -> new RegistrationIdempotencyResponseSnapshot("200", "{}", Map.of(header, "value")))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Unsafe idempotency snapshot header");
        }
        for (String value : List.of("csrf_abc", "auth_flow_sid=afs_secret", "Bearer abc")) {
            assertThatThrownBy(() -> new RegistrationIdempotencyResponseSnapshot("200", "{}", Map.of("X-Safe", value)))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Unsafe idempotency snapshot header")
                    .hasMessageNotContaining(value);
        }
    }

    @Test
    void startCommandRequiresPositiveTtl() {
        assertThatThrownBy(() -> new RegistrationIdempotencyStartCommand(
                RegistrationIdempotencyAction.REGISTRATION_START,
                new RegistrationIdempotencyKey("idem_12345678"),
                new RegistrationRequestHash(HASH),
                NOW,
                Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("ttl must be positive");
    }

    @Test
    void startResultDefinesExpectedOutcomes() {
        assertThat(RegistrationIdempotencyStartOutcome.values())
                .containsExactly(
                        RegistrationIdempotencyStartOutcome.STARTED,
                        RegistrationIdempotencyStartOutcome.DUPLICATE_IN_PROGRESS,
                        RegistrationIdempotencyStartOutcome.COMPLETED_REPLAY,
                        RegistrationIdempotencyStartOutcome.KEY_REUSED_WITH_DIFFERENT_REQUEST,
                        RegistrationIdempotencyStartOutcome.FAILED_EXISTING,
                        RegistrationIdempotencyStartOutcome.UNKNOWN_OUTCOME_EXISTING);
    }

    @Test
    void idempotencyModelsToStringRedactsKeysHashesAndSnapshots() {
        var snapshot = new RegistrationIdempotencyResponseSnapshot("200", "{\"secret\":\"value\"}",
                Map.of("X-Safe", "value"));
        var completed = record(RegistrationIdempotencyStatus.COMPLETED, snapshot);
        var startCommand = new RegistrationIdempotencyStartCommand(
                RegistrationIdempotencyAction.REGISTRATION_START,
                new RegistrationIdempotencyKey("idem_secret"),
                new RegistrationRequestHash(HASH),
                NOW,
                Duration.ofMinutes(5));

        assertThat(completed.toString()).doesNotContain("idem_secret", HASH, "secret", "serializedBody");
        assertThat(snapshot.toString()).doesNotContain("secret", "serializedBody");
        assertThat(startCommand.toString()).doesNotContain("idem_secret", HASH);
    }

    private static RegistrationIdempotencyRecord record(
            RegistrationIdempotencyStatus status,
            RegistrationIdempotencyResponseSnapshot snapshot) {
        return new RegistrationIdempotencyRecord(
                RegistrationIdempotencyAction.REGISTRATION_START,
                new RegistrationIdempotencyKey("idem_secret"),
                new RegistrationRequestHash(HASH),
                status,
                snapshot,
                NOW,
                NOW,
                NOW.plusSeconds(300));
    }
}
