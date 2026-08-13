package com.bct.ngtpa.apiservice.application.port.out;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class UsernameRecoveryIdempotencyRecordTest {

    private static final Instant NOW = Instant.parse("2026-08-08T00:00:00Z");
    private static final String DIGEST =
            "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";

    @Test
    void definesInProgressCompletedAndUnknownOutcomeStates() {
        assertThat(UsernameRecoveryIdempotencyRecord.Status.values()).containsExactly(
                UsernameRecoveryIdempotencyRecord.Status.IN_PROGRESS,
                UsernameRecoveryIdempotencyRecord.Status.COMPLETED,
                UsernameRecoveryIdempotencyRecord.Status.UNKNOWN_OUTCOME);
    }

    @Test
    void replaySnapshotAcceptsOpaqueNonblankValues() {
        assertThat(new UsernameRecoveryIdempotencyRecord.ReplaySnapshot(
                "snap-1234567890").value()).isEqualTo("snap-1234567890");
        for (String value : List.of("", " ")) {
            assertThatThrownBy(() ->
                    new UsernameRecoveryIdempotencyRecord.ReplaySnapshot(value))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("replay snapshot must not be null or blank");
        }
    }

    @Test
    void completedRecordRequiresReplaySnapshot() {
        assertThatThrownBy(() -> record(
                UsernameRecoveryIdempotencyRecord.Status.COMPLETED, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("completed record requires a replay snapshot");
    }

    @Test
    void nonCompletedRecordsMustNotCarryReplaySnapshot() {
        var snapshot = new UsernameRecoveryIdempotencyRecord.ReplaySnapshot(
                "snap-1234567890");
        for (var status : List.of(
                UsernameRecoveryIdempotencyRecord.Status.IN_PROGRESS,
                UsernameRecoveryIdempotencyRecord.Status.UNKNOWN_OUTCOME)) {
            assertThatThrownBy(() -> record(status, snapshot))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("non-completed record must not carry a replay snapshot");
        }
    }

    @Test
    void requiresExpiryAfterCreation() {
        assertThatThrownBy(() -> new UsernameRecoveryIdempotencyRecord(
                UsernameRecoveryIdempotencyAction.REQUEST,
                key(),
                fingerprint(),
                operationReference(),
                UsernameRecoveryIdempotencyRecord.Status.IN_PROGRESS,
                null,
                NOW,
                NOW,
                NOW))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("expiresAt must be after createdAt");
    }

    @Test
    void defaultsUpdatedAtToCreatedAt() {
        var record = new UsernameRecoveryIdempotencyRecord(
                UsernameRecoveryIdempotencyAction.REQUEST,
                key(),
                fingerprint(),
                operationReference(),
                UsernameRecoveryIdempotencyRecord.Status.IN_PROGRESS,
                null,
                NOW,
                null,
                NOW.plusSeconds(300));
        assertThat(record.updatedAt()).isEqualTo(NOW);
    }

    @Test
    void completedRecordCarriesReplaySnapshot() {
        var snapshot = new UsernameRecoveryIdempotencyRecord.ReplaySnapshot(
                "snap-1234567890");
        var record = record(UsernameRecoveryIdempotencyRecord.Status.COMPLETED, snapshot);
        assertThat(record.status()).isEqualTo(
                UsernameRecoveryIdempotencyRecord.Status.COMPLETED);
        assertThat(record.replaySnapshot()).isEqualTo(snapshot);
    }

    @Test
    void recordRetainsOpaqueReservationOwnerForCommitProof() {
        var record = record(UsernameRecoveryIdempotencyRecord.Status.IN_PROGRESS, null);
        assertThat(record.operationReference()).isEqualTo(operationReference());
        assertThat(record.toString()).doesNotContain("op-1234567890");
    }

    @Test
    void requiresReservationOwnerReference() {
        assertThatThrownBy(() -> new UsernameRecoveryIdempotencyRecord(
                UsernameRecoveryIdempotencyAction.REQUEST,
                key(),
                fingerprint(),
                null,
                UsernameRecoveryIdempotencyRecord.Status.IN_PROGRESS,
                null,
                NOW,
                NOW,
                NOW.plusSeconds(300)))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("operationReference must not be null");
    }

    @Test
    void toStringRedactsKeyFingerprintOwnerAndReplaySnapshot() {
        var record = record(
                UsernameRecoveryIdempotencyRecord.Status.COMPLETED,
                new UsernameRecoveryIdempotencyRecord.ReplaySnapshot("snap-secret"));
        assertThat(record.toString())
                .contains("action=REQUEST", "status=COMPLETED")
                .doesNotContain("idem_secret", DIGEST, "op-1234567890", "snap-secret");
    }

    private static UsernameRecoveryIdempotencyRecord record(
            UsernameRecoveryIdempotencyRecord.Status status,
            UsernameRecoveryIdempotencyRecord.ReplaySnapshot snapshot) {
        return new UsernameRecoveryIdempotencyRecord(
                UsernameRecoveryIdempotencyAction.REQUEST,
                new UsernameRecoveryIdempotencyKey("idem_secret"),
                new UsernameRecoveryIdempotencyFingerprint(DIGEST),
                operationReference(),
                status,
                snapshot,
                NOW,
                NOW,
                NOW.plusSeconds(300));
    }

    private static UsernameRecoveryIdempotencyKey key() {
        return new UsernameRecoveryIdempotencyKey("idem_12345678");
    }

    private static UsernameRecoveryIdempotencyFingerprint fingerprint() {
        return new UsernameRecoveryIdempotencyFingerprint(DIGEST);
    }

    private static UsernameRecoveryIdempotencyOperationReference operationReference() {
        return new UsernameRecoveryIdempotencyOperationReference("op-1234567890");
    }
}
