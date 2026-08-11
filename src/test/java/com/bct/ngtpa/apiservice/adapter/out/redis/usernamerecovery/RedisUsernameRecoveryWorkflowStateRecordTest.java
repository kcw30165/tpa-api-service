package com.bct.ngtpa.apiservice.adapter.out.redis.usernamerecovery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionId;
import com.bct.ngtpa.apiservice.application.dto.session.usernamerecovery.UsernameRecoveryWorkflowState;
import com.bct.ngtpa.apiservice.application.dto.session.usernamerecovery.UsernameRecoveryWorkflowStateRevision;
import com.bct.ngtpa.apiservice.domain.model.UsernameRecoveryCandidate;
import com.bct.ngtpa.apiservice.domain.model.UsernameRecoveryDeliveryState;
import com.bct.ngtpa.apiservice.domain.model.UsernameRecoverySchemeRef;
import com.bct.ngtpa.apiservice.domain.model.UsernameRecoverySessionCandidate;
import com.bct.ngtpa.apiservice.domain.model.UsernameRecoveryVerifiedDestination;
import com.bct.ngtpa.apiservice.domain.model.UsernameRecoveryWorkflowStatus;
import java.lang.reflect.RecordComponent;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;

/**
 * Group 1: workflow-state sidecar record round-trip and field confinement.
 *
 * <p>Verifies the record persists only the Model B sidecar fields, round-trips the
 * {@link UsernameRecoveryWorkflowState} aggregate losslessly, redacts protected values from
 * {@code toString}, and delegates invariant validation to the reconstructed application object.
 */
class RedisUsernameRecoveryWorkflowStateRecordTest {

    @Test
    void roundTripPreservesAllAllowedFields() {
        var state = activeWorkflowState()
                .reserve(new UsernameRecoverySchemeRef("sch_opaque-1"))
                .markRequested(new UsernameRecoverySchemeRef("sch_opaque-1"), "sender-ref-1",
                        Instant.parse("2026-08-08T00:02:00Z"));

        var restored = RedisUsernameRecoveryWorkflowStateRecord.from(state).toWorkflowState();

        assertThat(restored).isEqualTo(state);
        assertThat(restored.status()).isEqualTo(UsernameRecoveryWorkflowStatus.PARTIALLY_REQUESTED);
        assertThat(restored.revision().value()).isEqualTo(2);
    }

    @Test
    void forbiddenGenericEnvelopeFieldsAreAbsentFromRecordComponents() {
        var components = recordComponentNames(RedisUsernameRecoveryWorkflowStateRecord.class);
        var candidateComponents =
                recordComponentNames(RedisUsernameRecoveryWorkflowStateRecord.Candidate.class);

        assertThat(components)
                .contains("authFlowSessionId", "status", "revision", "destination",
                        "destinationChannel", "language", "candidates")
                .doesNotContain("scenario", "csrfHash", "createdAt", "updatedAt", "expiresAt",
                        "sessionRevision", "ttl");
        assertThat(candidateComponents)
                .contains("schemeRef", "accountEnv", "trustCode", "registered", "userName",
                        "deliveryState", "requestedAt", "senderReference")
                .doesNotContain("policyNo", "certNo", "schemeType", "termination", "dateOfBirth",
                        "captchaAnswer", "rawCsrfToken", "cookie");
    }

    @Test
    void recordToStringRedactsProtectedValues() {
        var record = RedisUsernameRecoveryWorkflowStateRecord.from(activeWorkflowState());

        assertThat(record.toString())
                .doesNotContain("afs_abc123")
                .doesNotContain("member@example.com")
                .doesNotContain("user-sch")
                .doesNotContain("sch_opaque-1")
                .doesNotContain("sch_opaque-2")
                .doesNotContain("sch_opaque-3");
    }

    @Test
    void invalidReconstructedSidecarIsRejectedByApplicationInvariants() {
        var oneCandidate = List.of(registeredCandidate("sch_opaque-1", "trust-1"));

        assertThatThrownBy(() -> new RedisUsernameRecoveryWorkflowStateRecord(
                "afs_abc123", "ACTIVE", 0, "member@example.com", "EMAIL", "zh-HK", List.of())
                .toWorkflowState())
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> new RedisUsernameRecoveryWorkflowStateRecord(
                "afs_abc123", "ACTIVE", -1, "member@example.com", "EMAIL", "zh-HK", oneCandidate)
                .toWorkflowState())
                .isInstanceOf(IllegalArgumentException.class);

        var duplicateSchemeRefs = List.of(
                registeredCandidate("sch_opaque-1", "trust-1"),
                registeredCandidate("sch_opaque-1", "trust-2"));
        assertThatThrownBy(() -> new RedisUsernameRecoveryWorkflowStateRecord(
                "afs_abc123", "ACTIVE", 0, "member@example.com", "EMAIL", "zh-HK",
                duplicateSchemeRefs)
                .toWorkflowState())
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> new RedisUsernameRecoveryWorkflowStateRecord(
                "afs_abc123", "NOT_A_STATUS", 0, "member@example.com", "EMAIL", "zh-HK",
                oneCandidate)
                .toWorkflowState())
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static List<String> recordComponentNames(Class<?> type) {
        return Arrays.stream(type.getRecordComponents()).map(RecordComponent::getName).toList();
    }

    private static RedisUsernameRecoveryWorkflowStateRecord.Candidate registeredCandidate(
            String schemeRef, String trustCode) {
        return new RedisUsernameRecoveryWorkflowStateRecord.Candidate(
                schemeRef, "HK", trustCode, true, "user-sch",
                UsernameRecoveryDeliveryState.AVAILABLE.name(), null, null);
    }

    private static UsernameRecoveryWorkflowState activeWorkflowState() {
        var registeredOne = new UsernameRecoverySessionCandidate(
                new UsernameRecoveryCandidate(
                        new UsernameRecoverySchemeRef("sch_opaque-1"),
                        "HK", "trust-1", true, "user-sch",
                        UsernameRecoveryDeliveryState.AVAILABLE),
                null, null);
        var registeredTwo = new UsernameRecoverySessionCandidate(
                new UsernameRecoveryCandidate(
                        new UsernameRecoverySchemeRef("sch_opaque-2"),
                        "HK", "trust-2", true, "user-sch-2",
                        UsernameRecoveryDeliveryState.AVAILABLE),
                null, null);
        var unregistered = new UsernameRecoverySessionCandidate(
                new UsernameRecoveryCandidate(
                        new UsernameRecoverySchemeRef("sch_opaque-3"),
                        "JP", "trust-3", false, null,
                        UsernameRecoveryDeliveryState.AVAILABLE),
                null, null);
        return new UsernameRecoveryWorkflowState(
                new AuthFlowSessionId("afs_abc123"),
                UsernameRecoveryWorkflowStatus.ACTIVE,
                new UsernameRecoveryWorkflowStateRevision(0),
                new UsernameRecoveryVerifiedDestination("member@example.com",
                        UsernameRecoveryVerifiedDestination.Channel.EMAIL),
                Locale.forLanguageTag("zh-HK"),
                List.of(registeredOne, registeredTwo, unregistered));
    }
}