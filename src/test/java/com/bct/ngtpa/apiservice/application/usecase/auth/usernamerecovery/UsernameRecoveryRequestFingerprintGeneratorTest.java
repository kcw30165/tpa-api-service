package com.bct.ngtpa.apiservice.application.usecase.auth.usernamerecovery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bct.ngtpa.apiservice.application.dto.auth.usernamerecovery.UsernameRecoveryRequestCommand;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionId;
import com.bct.ngtpa.apiservice.application.port.out.UsernameRecoveryIdempotencyFingerprint;
import com.bct.ngtpa.apiservice.domain.model.UsernameRecoverySchemeRef;
import org.junit.jupiter.api.Test;

/**
 * Verifies the Task 020 Request semantic fingerprint.
 *
 * <p>The fingerprint is derived from the REQUEST action namespace, the protected session id, and
 * the opaque Scheme-Ref - never from the client-provided idempotency key. Identical semantics
 * produce identical fingerprints; different sessions or Scheme-Refs produce different fingerprints;
 * changing only the client key never changes the fingerprint (so a same-key retry can be recognized
 * as the same semantic request).
 */
class UsernameRecoveryRequestFingerprintGeneratorTest {

  private final UsernameRecoveryRequestFingerprintGenerator generator =
      new UsernameRecoveryRequestFingerprintGenerator();

  @Test
  void sameSemanticsProduceIdenticalFingerprint() {
    UsernameRecoveryIdempotencyFingerprint first =
        generator.derive(command("afs_1", "sch_1", "key_12345678"));
    UsernameRecoveryIdempotencyFingerprint second =
        generator.derive(command("afs_1", "sch_1", "key_12345678"));

    assertThat(second).isEqualTo(first);
  }

  @Test
  void fingerprintIsDeterministicSha256HexShape() {
    UsernameRecoveryIdempotencyFingerprint fingerprint =
        generator.derive(command("afs_1", "sch_1", "key_12345678"));

    assertThat(fingerprint.value()).matches("^[a-f0-9]{64}$");
  }

  @Test
  void differentSessionIdProducesDifferentFingerprint() {
    UsernameRecoveryIdempotencyFingerprint sessionA =
        generator.derive(command("afs_1", "sch_1", "key_12345678"));
    UsernameRecoveryIdempotencyFingerprint sessionB =
        generator.derive(command("afs_2", "sch_1", "key_12345678"));

    assertThat(sessionB).isNotEqualTo(sessionA);
  }

  @Test
  void differentSchemeRefProducesDifferentFingerprint() {
    UsernameRecoveryIdempotencyFingerprint schemeA =
        generator.derive(command("afs_1", "sch_1", "key_12345678"));
    UsernameRecoveryIdempotencyFingerprint schemeB =
        generator.derive(command("afs_1", "sch_2", "key_12345678"));

    assertThat(schemeB).isNotEqualTo(schemeA);
  }

  @Test
  void changingOnlyTheClientKeyNeverChangesTheFingerprint() {
    UsernameRecoveryIdempotencyFingerprint keyA =
        generator.derive(command("afs_1", "sch_1", "key_12345678"));
    UsernameRecoveryIdempotencyFingerprint keyB =
        generator.derive(command("afs_1", "sch_1", "other_key_1234"));

    assertThat(keyB).isEqualTo(keyA);
  }

  @Test
  void fingerprintNeverContainsClientKeySessionOrSchemeRefValues() {
    UsernameRecoveryIdempotencyFingerprint fingerprint =
        generator.derive(command("afs_1", "sch_1", "key_12345678"));

    assertThat(fingerprint.toString())
        .isEqualTo("UsernameRecoveryIdempotencyFingerprint[redacted]");
    assertThat(fingerprint.toString()).doesNotContain("afs_1", "sch_1", "key_12345678");
  }

  @Test
  void rejectsNullCommand() {
    assertThatThrownBy(() -> generator.derive(null)).isInstanceOf(NullPointerException.class);
  }

  private static UsernameRecoveryRequestCommand command(
      String sessionId, String schemeRef, String idempotencyKey) {
    return new UsernameRecoveryRequestCommand(
        new AuthFlowSessionId(sessionId), new UsernameRecoverySchemeRef(schemeRef), idempotencyKey);
  }
}
