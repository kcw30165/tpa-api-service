package com.bct.ngtpa.apiservice.application.port.out;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Verifies the opaque Username Recovery idempotency key value object: safe opaque values are
 * accepted, unsafe tokens are rejected without echoing, and {@link #toString()} never discloses
 * the raw key.
 *
 * <p>Design references: {@code docs/design/6-username-recovery/username-recovery-api-contract-design.md}
 * section 4 (Idempotency-Key header on start/request/cancel) and
 * {@code docs/design/6-username-recovery/username-recovery-session-redis-design.md} section 8
 * (safe persistence with no raw session/CSRF values). Mirrors
 * {@code RegistrationIdempotencyKey} safety rules.
 */
class UsernameRecoveryIdempotencyKeyTest {

    @Test
    void acceptsSafeOpaqueValues() {
        assertThat(new UsernameRecoveryIdempotencyKey("idem_12345678").value())
                .isEqualTo("idem_12345678");
        assertThat(new UsernameRecoveryIdempotencyKey("550e8400-e29b-41d4-a716-446655440000").value())
                .isEqualTo("550e8400-e29b-41d4-a716-446655440000");
        assertThat(new UsernameRecoveryIdempotencyKey("abcDEF123_-").value())
                .isEqualTo("abcDEF123_-");
    }

    @Test
    void rejectsUnsafeValuesWithoutEchoing() {
        List<String> unsafe = List.of(
                "",
                "   ",
                "abc def",
                "abc:def",
                "abc/def",
                "member@example.com",
                "91234567",
                "csrf_abc",
                "otp_abc",
                "auth_flow_sid=afs_secret",
                "Bearer abc",
                "Cookie abc");
        for (String value : unsafe) {
            var assertion = assertThatThrownBy(() -> new UsernameRecoveryIdempotencyKey(value))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Invalid username recovery idempotency key");
            if (!value.isEmpty()) {
                assertion.hasMessageNotContaining(value);
            }
        }
    }

    @Test
    void toStringNeverDisclosesTheKey() {
        assertThat(new UsernameRecoveryIdempotencyKey("idem_secret").toString())
                .isEqualTo("UsernameRecoveryIdempotencyKey[redacted]");
    }
}
