package com.bct.ngtpa.apiservice.application.port.out;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Verifies the opaque semantic fingerprint value object: it carries only an opaque digest, rejects
 * any non-digest input without echoing, and {@link #toString()} never discloses the fingerprint.
 *
 * <p>Design references: {@code
 * docs/design/6-username-recovery/username-recovery-session-redis-design.md} section 1 (recovery
 * idempotency hash; the captcha answer is never included) and section 8 (safe persistence). The
 * hashing algorithm itself belongs to Task 014; this contract only holds the opaque result. Mirrors
 * {@code RegistrationRequestHash} opaque hex-digest format.
 */
class UsernameRecoveryIdempotencyFingerprintTest {

  private static final String DIGEST =
      "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";

  @Test
  void acceptsOpaqueHexDigests() {
    assertThat(new UsernameRecoveryIdempotencyFingerprint(DIGEST).value()).isEqualTo(DIGEST);
  }

  @Test
  void rejectsNonDigestInputWithoutEchoing() {
    List<String> unsafe =
        List.of(
            "",
            "   ",
            "not-hex",
            "abc",
            DIGEST.toUpperCase(),
            "member@example.com",
            "csrf_abc",
            "auth_flow_sid=afs_secret");
    for (String value : unsafe) {
      var assertion =
          assertThatThrownBy(() -> new UsernameRecoveryIdempotencyFingerprint(value))
              .isInstanceOf(IllegalArgumentException.class)
              .hasMessage("Invalid username recovery semantic fingerprint");
      if (!value.isEmpty()) {
        assertion.hasMessageNotContaining(value);
      }
    }
  }

  @Test
  void toStringNeverDisclosesTheFingerprint() {
    assertThat(new UsernameRecoveryIdempotencyFingerprint(DIGEST).toString())
        .isEqualTo("UsernameRecoveryIdempotencyFingerprint[redacted]");
  }
}
