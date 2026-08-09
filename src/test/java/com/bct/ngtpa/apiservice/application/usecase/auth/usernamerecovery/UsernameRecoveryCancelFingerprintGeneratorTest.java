package com.bct.ngtpa.apiservice.application.usecase.auth.usernamerecovery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bct.ngtpa.apiservice.application.dto.auth.usernamerecovery.UsernameRecoveryCancelCommand;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionId;
import com.bct.ngtpa.apiservice.application.port.out.UsernameRecoveryIdempotencyFingerprint;
import org.junit.jupiter.api.Test;

/**
 * Verifies the Task 021 {@link UsernameRecoveryCancelFingerprintGenerator}.
 *
 * <p>The CANCEL semantic fingerprint covers the CANCEL action namespace plus the protected
 * session id only. It must never be derived from the client-provided Idempotency-Key or any raw
 * CSRF value, and the digest value must never disclose the session id, key, or CSRF material.
 */
class UsernameRecoveryCancelFingerprintGeneratorTest {

    private static final String SESSION_ID = "afs_opaque-1";
    private static final String KEY_A = "idem_aaaaaaaa";
    private static final String KEY_B = "idem_bbbbbbbb";

    private final UsernameRecoveryCancelFingerprintGenerator generator =
            new UsernameRecoveryCancelFingerprintGenerator();

    @Test
    void deriveIsDeterministicForTheSameSemantics() {
        UsernameRecoveryIdempotencyFingerprint first = generator.derive(command(SESSION_ID, KEY_A));
        UsernameRecoveryIdempotencyFingerprint second = generator.derive(command(SESSION_ID, KEY_A));

        assertThat(first).isEqualTo(second);
    }

    @Test
    void differentSessionProducesDifferentFingerprint() {
        UsernameRecoveryIdempotencyFingerprint first = generator.derive(command(SESSION_ID, KEY_A));
        UsernameRecoveryIdempotencyFingerprint second = generator.derive(command("afs_other-2", KEY_A));

        assertThat(first).isNotEqualTo(second);
    }

    @Test
    void clientKeyIsExcludedFromTheFingerprint() {
        UsernameRecoveryIdempotencyFingerprint withKeyA = generator.derive(command(SESSION_ID, KEY_A));
        UsernameRecoveryIdempotencyFingerprint withKeyB = generator.derive(command(SESSION_ID, KEY_B));

        assertThat(withKeyA).isEqualTo(withKeyB);
        assertThat(withKeyA.value()).doesNotContain(KEY_A);
        assertThat(withKeyA.value()).doesNotContain(KEY_B);
    }

    @Test
    void digestNeverDisclosesSessionOrCsrfMaterial() {
        UsernameRecoveryIdempotencyFingerprint fingerprint = generator.derive(command(SESSION_ID, KEY_A));

        assertThat(fingerprint.value())
                .doesNotContain(SESSION_ID)
                .doesNotContain("csrf")
                .doesNotContain("afs_")
                .doesNotContain("idem_");
    }

    @Test
    void fingerprintIsASha256HexDigest() {
        UsernameRecoveryIdempotencyFingerprint fingerprint = generator.derive(command(SESSION_ID, KEY_A));

        assertThat(fingerprint.value()).matches("^[a-f0-9]{64}$");
    }

    @Test
    void nullCommandIsRejected() {
        assertThatThrownBy(() -> generator.derive(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("command");
    }

    private static UsernameRecoveryCancelCommand command(String sessionId, String key) {
        return new UsernameRecoveryCancelCommand(new AuthFlowSessionId(sessionId), key);
    }
}
