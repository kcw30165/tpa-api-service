package com.bct.ngtpa.apiservice.application.dto.session.usernamerecovery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionId;
import com.bct.ngtpa.apiservice.domain.model.UsernameRecoveryCandidate;
import com.bct.ngtpa.apiservice.domain.model.UsernameRecoveryDeliveryState;
import com.bct.ngtpa.apiservice.domain.model.UsernameRecoverySchemeRef;
import com.bct.ngtpa.apiservice.domain.model.UsernameRecoverySessionCandidate;
import com.bct.ngtpa.apiservice.domain.model.UsernameRecoveryVerifiedDestination;
import com.bct.ngtpa.apiservice.domain.model.UsernameRecoveryWorkflowStatus;
import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;

/**
 * Verifies the Model B Username Recovery workflow-state sidecar aggregate: canonical
 * {@link AuthFlowSessionId} identity, feature-owned fields only, construction invariants,
 * candidate delivery transitions, completion projection, workflow-state revision semantics, and
 * safe redaction.
 *
 * <p>Model B decision {@code MODEL_B_GENERIC_ENVELOPE_TYPED_SIDECAR} (audit evidence 003): the
 * sidecar owns language, verified destination, candidates, delivery transitions, the workflow
 * projection, and the workflow-state revision. It deliberately contains no scenario, CSRF hash,
 * created/updated/expiry timestamps, cookie data, or generic lifecycle revision, and exposes no
 * lifecycle behavior (expiry, terminality, cancellation, deletion).
 */
class UsernameRecoveryWorkflowStateTest {

    private static final AuthFlowSessionId SESSION_ID = new AuthFlowSessionId("afs_opaque-1");
    private static final UsernameRecoveryWorkflowStateRevision ZERO =
            new UsernameRecoveryWorkflowStateRevision(0);
    private static final UsernameRecoveryVerifiedDestination DESTINATION =
            new UsernameRecoveryVerifiedDestination(
                    "member@example.com", UsernameRecoveryVerifiedDestination.Channel.EMAIL);
    private static final Locale LANGUAGE = Locale.ENGLISH;
    private static final Instant REQUESTED_AT = Instant.parse("2026-01-01T00:01:00Z");

    private static UsernameRecoverySessionCandidate registered(
            String scheme, String env, String trust, UsernameRecoveryDeliveryState state) {
        return new UsernameRecoverySessionCandidate(
                new UsernameRecoveryCandidate(
                        new UsernameRecoverySchemeRef(scheme), env, trust, true, "user-" + scheme, state),
                null,
                null);
    }

    private static UsernameRecoverySessionCandidate unregistered(
            String scheme, String env, String trust, UsernameRecoveryDeliveryState state) {
        return new UsernameRecoverySessionCandidate(
                new UsernameRecoveryCandidate(
                        new UsernameRecoverySchemeRef(scheme), env, trust, false, null, state),
                null,
                null);
    }

    private static UsernameRecoverySessionCandidate requestedRegistered(
            String scheme, String env, String trust) {
        return new UsernameRecoverySessionCandidate(
                new UsernameRecoveryCandidate(
                        new UsernameRecoverySchemeRef(scheme),
                        env,
                        trust,
                        true,
                        "user-" + scheme,
                        UsernameRecoveryDeliveryState.REQUESTED),
                REQUESTED_AT,
                "sender-ref-" + scheme);
    }

    private static UsernameRecoveryWorkflowState state(
            UsernameRecoveryWorkflowStatus status,
            UsernameRecoveryWorkflowStateRevision revision,
            UsernameRecoverySessionCandidate... candidates) {
        return new UsernameRecoveryWorkflowState(
                SESSION_ID, status, revision, DESTINATION, LANGUAGE, Arrays.asList(candidates));
    }

    private static UsernameRecoverySchemeRef sch(String value) {
        return new UsernameRecoverySchemeRef(value);
    }

    @Test
    void retainsCanonicalAuthFlowSessionIdIdentity() {
        UsernameRecoveryWorkflowState value =
                state(UsernameRecoveryWorkflowStatus.ACTIVE, ZERO,
                        registered("sch_1", "DB", "DBA", UsernameRecoveryDeliveryState.AVAILABLE));

        assertThat(value.authFlowSessionId()).isEqualTo(SESSION_ID);
        assertThat(UsernameRecoveryWorkflowState.class.getRecordComponents()[0].getType())
                .isEqualTo(AuthFlowSessionId.class);
    }

    @Test
    void retainsFeatureOwnedFields() {
        UsernameRecoverySessionCandidate candidate =
                registered("sch_1", "DB", "DBA", UsernameRecoveryDeliveryState.AVAILABLE);
        UsernameRecoveryWorkflowState value = state(
                UsernameRecoveryWorkflowStatus.ACTIVE, ZERO, candidate);

        assertThat(value.status()).isEqualTo(UsernameRecoveryWorkflowStatus.ACTIVE);
        assertThat(value.revision()).isEqualTo(ZERO);
        assertThat(value.destination()).isEqualTo(DESTINATION);
        assertThat(value.language()).isEqualTo(LANGUAGE);
        assertThat(value.candidates()).containsExactly(candidate);
    }

    @Test
    void rejectsNullAuthFlowSessionId() {
        assertThatThrownBy(() -> new UsernameRecoveryWorkflowState(
                        null,
                        UsernameRecoveryWorkflowStatus.ACTIVE,
                        ZERO,
                        DESTINATION,
                        LANGUAGE,
                        List.of(registered("sch_1", "DB", "DBA", UsernameRecoveryDeliveryState.AVAILABLE))))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("authFlowSessionId must not be null");
    }

    @Test
    void rejectsNullStatus() {
        assertThatThrownBy(() -> new UsernameRecoveryWorkflowState(
                        SESSION_ID,
                        null,
                        ZERO,
                        DESTINATION,
                        LANGUAGE,
                        List.of(registered("sch_1", "DB", "DBA", UsernameRecoveryDeliveryState.AVAILABLE))))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("status must not be null");
    }

    @Test
    void rejectsNullRevision() {
        assertThatThrownBy(() -> new UsernameRecoveryWorkflowState(
                        SESSION_ID,
                        UsernameRecoveryWorkflowStatus.ACTIVE,
                        null,
                        DESTINATION,
                        LANGUAGE,
                        List.of(registered("sch_1", "DB", "DBA", UsernameRecoveryDeliveryState.AVAILABLE))))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("revision must not be null");
    }

    @Test
    void rejectsNullDestination() {
        assertThatThrownBy(() -> new UsernameRecoveryWorkflowState(
                        SESSION_ID,
                        UsernameRecoveryWorkflowStatus.ACTIVE,
                        ZERO,
                        null,
                        LANGUAGE,
                        List.of(registered("sch_1", "DB", "DBA", UsernameRecoveryDeliveryState.AVAILABLE))))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("destination must not be null");
    }

    @Test
    void rejectsNullLanguage() {
        assertThatThrownBy(() -> new UsernameRecoveryWorkflowState(
                        SESSION_ID,
                        UsernameRecoveryWorkflowStatus.ACTIVE,
                        ZERO,
                        DESTINATION,
                        null,
                        List.of(registered("sch_1", "DB", "DBA", UsernameRecoveryDeliveryState.AVAILABLE))))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("language must not be null");
    }

    @Test
    void rejectsNullCandidatesList() {
        assertThatThrownBy(() -> new UsernameRecoveryWorkflowState(
                        SESSION_ID, UsernameRecoveryWorkflowStatus.ACTIVE, ZERO, DESTINATION, LANGUAGE, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("workflow state requires at least one candidate");
    }

    @Test
    void immutablyCopiesCandidates() {
        List<UsernameRecoverySessionCandidate> mutable = new java.util.ArrayList<>();
        mutable.add(registered("sch_1", "DB", "DBA", UsernameRecoveryDeliveryState.AVAILABLE));
        UsernameRecoveryWorkflowState value = new UsernameRecoveryWorkflowState(
                SESSION_ID, UsernameRecoveryWorkflowStatus.ACTIVE, ZERO, DESTINATION, LANGUAGE, mutable);
        mutable.clear();

        assertThat(value.candidates()).hasSize(1);
        assertThatThrownBy(() -> value.candidates().add(
                        registered("sch_2", "HK", "HKA", UsernameRecoveryDeliveryState.AVAILABLE)))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void requiresAtLeastOneCandidate() {
        assertThatThrownBy(() -> state(UsernameRecoveryWorkflowStatus.ACTIVE, ZERO))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("workflow state requires at least one candidate");
    }

    @Test
    void requiresAtLeastOneRegisteredCandidate() {
        assertThatThrownBy(() -> state(
                        UsernameRecoveryWorkflowStatus.ACTIVE,
                        ZERO,
                        unregistered("sch_9", "HK", "HKA", UsernameRecoveryDeliveryState.AVAILABLE)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("workflow state requires at least one registered candidate");
    }

    @Test
    void rejectsDuplicateSchemeRefs() {
        assertThatThrownBy(() -> state(
                        UsernameRecoveryWorkflowStatus.ACTIVE,
                        ZERO,
                        registered("sch_1", "DB", "DBA", UsernameRecoveryDeliveryState.AVAILABLE),
                        registered("sch_1", "HK", "HKA", UsernameRecoveryDeliveryState.AVAILABLE)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("duplicate schemeRef is rejected");
    }

    @Test
    void rejectsDuplicateAccountEnvTrustCodePairs() {
        assertThatThrownBy(() -> state(
                        UsernameRecoveryWorkflowStatus.ACTIVE,
                        ZERO,
                        registered("sch_1", "DB", "DBA", UsernameRecoveryDeliveryState.AVAILABLE),
                        registered("sch_2", "DB", "DBA", UsernameRecoveryDeliveryState.AVAILABLE)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("duplicate accountEnv+trustCode candidate is rejected");
    }

    @Test
    void completedProjectionRequiresEveryRegisteredCandidateRequested() {
        assertThatThrownBy(() -> state(
                        UsernameRecoveryWorkflowStatus.COMPLETED,
                        ZERO,
                        registered("sch_1", "DB", "DBA", UsernameRecoveryDeliveryState.AVAILABLE)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("COMPLETED requires every registered candidate to be REQUESTED");
    }

    @Test
    void everyRegisteredCandidateRequestedRequiresCompletedProjection() {
        assertThatThrownBy(() -> state(
                        UsernameRecoveryWorkflowStatus.ACTIVE,
                        ZERO,
                        requestedRegistered("sch_1", "DB", "DBA")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("every registered candidate REQUESTED requires COMPLETED status");
    }

    @Test
    void completedProjectionAcceptsEveryRegisteredCandidateRequestedWithUnregisteredPending() {
        UsernameRecoveryWorkflowState value = state(
                UsernameRecoveryWorkflowStatus.COMPLETED,
                ZERO,
                requestedRegistered("sch_1", "DB", "DBA"),
                unregistered("sch_9", "HK", "HKA", UsernameRecoveryDeliveryState.AVAILABLE));

        assertThat(value.status()).isEqualTo(UsernameRecoveryWorkflowStatus.COMPLETED);
    }

    @Test
    void recordCarriesExactlyTheSixSidecarComponents() {
        List<String> names = Arrays.stream(UsernameRecoveryWorkflowState.class.getRecordComponents())
                .map(RecordComponent::getName)
                .toList();

        assertThat(names).containsExactly(
                "authFlowSessionId", "status", "revision", "destination", "language", "candidates");
    }

    @Test
    void doesNotExposeGenericLifecycleMethods() {
        List<String> methodNames = Arrays.stream(UsernameRecoveryWorkflowState.class.getMethods())
                .map(Method::getName)
                .toList();

        assertThat(methodNames)
                .doesNotContain("isExpired", "isTerminal", "cancel", "expire")
                .contains("reserve", "markRequested", "markFailed", "markUnknownOutcome",
                        "markCancelled");
    }

    @Test
    void markCancelledPreservesCandidateFactsAndAdvancesRevisionOnce() {
        UsernameRecoverySessionCandidate candidate =
                registered("sch_1", "DB", "DBA", UsernameRecoveryDeliveryState.RESERVED);
        UsernameRecoveryWorkflowState value = state(
                UsernameRecoveryWorkflowStatus.ACTIVE, ZERO, candidate);

        UsernameRecoveryWorkflowState cancelled = value.markCancelled();

        assertThat(cancelled.status()).isEqualTo(UsernameRecoveryWorkflowStatus.CANCELLED);
        assertThat(cancelled.revision().value()).isEqualTo(1);
        assertThat(cancelled.candidates()).containsExactly(candidate);
        assertThat(cancelled.destination()).isEqualTo(DESTINATION);
        assertThat(cancelled.language()).isEqualTo(LANGUAGE);
        assertThat(cancelled.authFlowSessionId()).isEqualTo(SESSION_ID);
        assertThat(cancelled.markCancelled()).isSameAs(cancelled);
        assertThat(cancelled.markCancelled().revision().value()).isEqualTo(1);
    }

    @Test
    void markCancelledSupportsPartiallyRequestedAndRejectsCompleted() {
        UsernameRecoveryWorkflowState partially = state(
                UsernameRecoveryWorkflowStatus.PARTIALLY_REQUESTED, ZERO,
                requestedRegistered("sch_1", "DB", "DBA"),
                registered("sch_2", "HK", "HKA", UsernameRecoveryDeliveryState.AVAILABLE));
        assertThat(partially.markCancelled().status())
                .isEqualTo(UsernameRecoveryWorkflowStatus.CANCELLED);

        UsernameRecoveryWorkflowState completed = state(
                UsernameRecoveryWorkflowStatus.COMPLETED, ZERO,
                requestedRegistered("sch_1", "DB", "DBA"));
        assertThatThrownBy(completed::markCancelled)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("workflow state is terminal");
    }

    @Test
    void reserveRequiresAvailableRegisteredCandidate() {
        UsernameRecoveryWorkflowState value =
                state(UsernameRecoveryWorkflowStatus.ACTIVE, ZERO,
                        registered("sch_1", "DB", "DBA", UsernameRecoveryDeliveryState.AVAILABLE));

        UsernameRecoveryWorkflowState reserved = value.reserve(sch("sch_1"));

        assertThat(reserved.candidates().get(0).deliveryState())
                .isEqualTo(UsernameRecoveryDeliveryState.RESERVED);
        assertThat(reserved.status()).isEqualTo(UsernameRecoveryWorkflowStatus.ACTIVE);
        assertThat(reserved.revision().value()).isEqualTo(1);
    }

    @Test
    void reserveRejectsUnregisteredCandidate() {
        UsernameRecoveryWorkflowState value = state(
                UsernameRecoveryWorkflowStatus.ACTIVE,
                ZERO,
                registered("sch_1", "DB", "DBA", UsernameRecoveryDeliveryState.AVAILABLE),
                unregistered("sch_9", "HK", "HKA", UsernameRecoveryDeliveryState.AVAILABLE));

        assertThatThrownBy(() -> value.reserve(sch("sch_9")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("unregistered candidate cannot participate");
    }

    @Test
    void reserveRejectsNonAvailableCandidate() {
        UsernameRecoveryWorkflowState value = state(
                UsernameRecoveryWorkflowStatus.ACTIVE,
                ZERO,
                registered("sch_1", "DB", "DBA", UsernameRecoveryDeliveryState.RESERVED));

        assertThatThrownBy(() -> value.reserve(sch("sch_1")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("candidate is not available for reservation");
    }

    @Test
    void reserveRejectsUnknownSchemeRef() {
        UsernameRecoveryWorkflowState value =
                state(UsernameRecoveryWorkflowStatus.ACTIVE, ZERO,
                        registered("sch_1", "DB", "DBA", UsernameRecoveryDeliveryState.AVAILABLE));

        assertThatThrownBy(() -> value.reserve(sch("sch_fabricated")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("unknown schemeRef");
    }

    @Test
    void markRequestedRequiresReservedCandidate() {
        UsernameRecoveryWorkflowState value = state(
                UsernameRecoveryWorkflowStatus.ACTIVE,
                ZERO,
                registered("sch_1", "DB", "DBA", UsernameRecoveryDeliveryState.AVAILABLE));

        UsernameRecoveryWorkflowState requested = value.reserve(sch("sch_1"))
                .markRequested(sch("sch_1"), "sender-ref-1", REQUESTED_AT);

        assertThat(requested.candidates().get(0).deliveryState())
                .isEqualTo(UsernameRecoveryDeliveryState.REQUESTED);
        assertThat(requested.candidates().get(0).requestedAt()).isEqualTo(REQUESTED_AT);
        assertThat(requested.candidates().get(0).senderReference()).isEqualTo("sender-ref-1");
    }

    @Test
    void markRequestedCompletesWhenEveryRegisteredCandidateRequested() {
        UsernameRecoveryWorkflowState value = state(
                UsernameRecoveryWorkflowStatus.ACTIVE,
                ZERO,
                registered("sch_1", "DB", "DBA", UsernameRecoveryDeliveryState.AVAILABLE));

        UsernameRecoveryWorkflowState completed = value.reserve(sch("sch_1"))
                .markRequested(sch("sch_1"), "sender-ref-1", REQUESTED_AT);

        assertThat(completed.status()).isEqualTo(UsernameRecoveryWorkflowStatus.COMPLETED);
    }

    @Test
    void markRequestedTransitionsToPartiallyRequestedUntilAllRegisteredRequested() {
        UsernameRecoveryWorkflowState value = state(
                UsernameRecoveryWorkflowStatus.ACTIVE,
                ZERO,
                registered("sch_1", "DB", "DBA", UsernameRecoveryDeliveryState.AVAILABLE),
                registered("sch_2", "HK", "HKA", UsernameRecoveryDeliveryState.AVAILABLE));

        UsernameRecoveryWorkflowState partially = value.reserve(sch("sch_1"))
                .markRequested(sch("sch_1"), "sender-ref-1", REQUESTED_AT);

        assertThat(partially.status()).isEqualTo(UsernameRecoveryWorkflowStatus.PARTIALLY_REQUESTED);

        UsernameRecoveryWorkflowState completed = partially.reserve(sch("sch_2"))
                .markRequested(sch("sch_2"), "sender-ref-2", REQUESTED_AT);

        assertThat(completed.status()).isEqualTo(UsernameRecoveryWorkflowStatus.COMPLETED);
    }

    @Test
    void unregisteredCandidatesDoNotBlockCompletion() {
        UsernameRecoveryWorkflowState value = state(
                UsernameRecoveryWorkflowStatus.ACTIVE,
                ZERO,
                registered("sch_1", "DB", "DBA", UsernameRecoveryDeliveryState.AVAILABLE),
                unregistered("sch_9", "HK", "HKA", UsernameRecoveryDeliveryState.AVAILABLE));

        UsernameRecoveryWorkflowState completed = value.reserve(sch("sch_1"))
                .markRequested(sch("sch_1"), "sender-ref-1", REQUESTED_AT);

        assertThat(completed.status()).isEqualTo(UsernameRecoveryWorkflowStatus.COMPLETED);
    }

    @Test
    void markRequestedRejectsNonReservedCandidate() {
        UsernameRecoveryWorkflowState value = state(
                UsernameRecoveryWorkflowStatus.ACTIVE,
                ZERO,
                registered("sch_1", "DB", "DBA", UsernameRecoveryDeliveryState.AVAILABLE));

        assertThatThrownBy(() -> value.markRequested(sch("sch_1"), "sender-ref-1", REQUESTED_AT))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("candidate must be reserved before requesting");
    }

    @Test
    void markFailedRequiresReservedCandidate() {
        UsernameRecoveryWorkflowState value = state(
                UsernameRecoveryWorkflowStatus.ACTIVE,
                ZERO,
                registered("sch_1", "DB", "DBA", UsernameRecoveryDeliveryState.AVAILABLE));

        UsernameRecoveryWorkflowState failed = value.reserve(sch("sch_1")).markFailed(sch("sch_1"));

        assertThat(failed.candidates().get(0).deliveryState())
                .isEqualTo(UsernameRecoveryDeliveryState.FAILED);
        assertThat(failed.status()).isEqualTo(UsernameRecoveryWorkflowStatus.ACTIVE);
    }

    @Test
    void markFailedRejectsNonReservedCandidate() {
        UsernameRecoveryWorkflowState value = state(
                UsernameRecoveryWorkflowStatus.ACTIVE,
                ZERO,
                registered("sch_1", "DB", "DBA", UsernameRecoveryDeliveryState.AVAILABLE));

        assertThatThrownBy(() -> value.markFailed(sch("sch_1")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("candidate must be reserved before failing");
    }

    @Test
    void markUnknownOutcomeRequiresReservedCandidate() {
        UsernameRecoveryWorkflowState value = state(
                UsernameRecoveryWorkflowStatus.ACTIVE,
                ZERO,
                registered("sch_1", "DB", "DBA", UsernameRecoveryDeliveryState.AVAILABLE));

        UsernameRecoveryWorkflowState unknown = value.reserve(sch("sch_1"))
                .markUnknownOutcome(sch("sch_1"));

        assertThat(unknown.candidates().get(0).deliveryState())
                .isEqualTo(UsernameRecoveryDeliveryState.UNKNOWN_OUTCOME);
    }

    @Test
    void markUnknownOutcomeRejectsNonReservedCandidate() {
        UsernameRecoveryWorkflowState value = state(
                UsernameRecoveryWorkflowStatus.ACTIVE,
                ZERO,
                registered("sch_1", "DB", "DBA", UsernameRecoveryDeliveryState.AVAILABLE));

        assertThatThrownBy(() -> value.markUnknownOutcome(sch("sch_1")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("candidate must be reserved before recording an unknown outcome");
    }

    @Test
    void revisionAdvancesExactlyOncePerCandidateMutation() {
        UsernameRecoveryWorkflowState value = state(
                UsernameRecoveryWorkflowStatus.ACTIVE,
                ZERO,
                registered("sch_1", "DB", "DBA", UsernameRecoveryDeliveryState.AVAILABLE),
                registered("sch_2", "HK", "HKA", UsernameRecoveryDeliveryState.AVAILABLE));

        UsernameRecoveryWorkflowState r1 = value.reserve(sch("sch_1"));
        assertThat(r1.revision().value()).isEqualTo(1);
        UsernameRecoveryWorkflowState r2 = r1.markRequested(sch("sch_1"), "sender-ref-1", REQUESTED_AT);
        assertThat(r2.revision().value()).isEqualTo(2);
        UsernameRecoveryWorkflowState r3 = r2.reserve(sch("sch_2"));
        assertThat(r3.revision().value()).isEqualTo(3);
        UsernameRecoveryWorkflowState r4 = r3.markRequested(sch("sch_2"), "sender-ref-2", REQUESTED_AT);
        assertThat(r4.revision().value()).isEqualTo(4);
    }

    @Test
    void concurrentMutationsFromSameSnapshotProduceSameRevisionRequiringPersistenceCas() {
        UsernameRecoveryWorkflowState value = state(
                UsernameRecoveryWorkflowStatus.ACTIVE,
                ZERO,
                registered("sch_1", "DB", "DBA", UsernameRecoveryDeliveryState.AVAILABLE),
                registered("sch_2", "HK", "HKA", UsernameRecoveryDeliveryState.AVAILABLE));

        UsernameRecoveryWorkflowState a = value.reserve(sch("sch_1"));
        UsernameRecoveryWorkflowState b = value.reserve(sch("sch_2"));

        assertThat(a.revision().value()).isEqualTo(1);
        assertThat(b.revision().value()).isEqualTo(1);
        assertThat(a.candidates().get(0).deliveryState()).isEqualTo(UsernameRecoveryDeliveryState.RESERVED);
        assertThat(b.candidates().get(1).deliveryState()).isEqualTo(UsernameRecoveryDeliveryState.RESERVED);
    }

    @Test
    void toStringRedactsSessionIdDestinationUsernamesSchemeRefsAndSenderReferences() {
        UsernameRecoveryWorkflowState value = state(
                UsernameRecoveryWorkflowStatus.ACTIVE,
                ZERO,
                registered("sch_1", "DB", "DBA", UsernameRecoveryDeliveryState.AVAILABLE),
                unregistered("sch_9", "HK", "HKA", UsernameRecoveryDeliveryState.AVAILABLE));

        String text = value.toString();

        assertThat(text)
                .doesNotContain("afs_opaque", "member@example.com", "user-", "sch_1", "sch_9")
                .contains("candidateCount=2", "authFlowSessionId=<redacted>", "destination=<redacted>");
    }
}
