package com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.otp;

import static org.assertj.core.api.Assertions.*;
import com.bct.ngtpa.apiservice.application.dto.auth.forgotpassword.idempotency.*;
import java.lang.reflect.RecordComponent;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class ForgotPasswordDeliveryReplayProtectionSubtask005003FailFirstTest {
    @Test
    void commitStateHasOnlyApprovedFields() {
        assertThat(
                Arrays.stream(ForgotPasswordOtpCommitState.class.getRecordComponents()).map(RecordComponent::getName))
                .containsExactly("authFlowSessionId", "otpChallengeId", "committedAt", "challengeExpiresAt");
    }

    @Test
    void referenceHasAtLeast128BitsAndIsOpaque() {
        var g = new SecureForgotPasswordOtpCommitRefGenerator();
        String a = g.generate(), b = g.generate();
        assertThat(a).isNotEqualTo(b).matches("^[A-Za-z0-9_-]{22,}$");
        assertThat(a).doesNotContain("afs_", "otp_", "req-", "member@example.com");
        assertThat(g.toString()).doesNotContain(a);
    }

    @Test
    void replaySnapshotRemainsOpaqueOnly() {
        assertThat(Arrays.stream(ForgotPasswordIdempotencyReplaySnapshot.class.getRecordComponents())
                .map(RecordComponent::getName))
                .containsExactly("outcomeCode", "opaqueCommitRef", "safeHeaders", "identityType");
    }

    @Test
    void oldRecordsAllowNullPendingReference() {
        var r = new ForgotPasswordIdempotencyRecord(ForgotPasswordIdempotencyAction.START,
                new ForgotPasswordIdempotencyKey("abcdefgh"), new ForgotPasswordRequestHash("a".repeat(64)),
                ForgotPasswordIdempotencyStatus.PROCESSING, null, Instant.EPOCH, Instant.EPOCH,
                Instant.EPOCH.plusSeconds(30), null);
        assertThat(r.pendingOpaqueCommitRef()).isNull();
        assertThat(r.toString()).doesNotContain("abcdefgh");
    }

    @Test
    void retentionUsesLaterBoundary() {
        var p = new ForgotPasswordOtpCommitRetentionPolicy(Duration.ofSeconds(60), Duration.ofSeconds(600));
        assertThat(p.ttl(Instant.EPOCH, Instant.EPOCH.plusSeconds(300), Instant.EPOCH.plusSeconds(500)))
                .isEqualTo(Duration.ofSeconds(500));
    }
}
