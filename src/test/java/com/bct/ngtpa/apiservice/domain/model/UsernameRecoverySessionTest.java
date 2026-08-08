package com.bct.ngtpa.apiservice.domain.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.junit.jupiter.api.Test;

/**
 * Verifies the protected Username Recovery session aggregate: construction and structural
 * invariants, lifecycle, pure delivery transitions, completion, optimistic-concurrency revision
 * semantics, and safe redaction.
 *
 * <p>Design references: {@code docs/design/6-username-recovery/username-recovery-session-redis-design.md}
 * sections 2-4 (lifecycle {@code ACTIVE -> PARTIALLY_REQUESTED -> COMPLETED};
 * {@code ACTIVE/PARTIALLY_REQUESTED -> CANCELLED or EXPIRED}; completion counts registered
 * candidates only) and {@code docs/design/6-username-recovery/username-recovery-solution-design.md}
 * sections 6-7 (sender {@code ACCEPTED} maps the candidate to {@code REQUESTED}; requested timestamp
 * and sender/idempotency reference required for replay safety) and
 * {@code docs/design/6-username-recovery/username-recovery-test-traceability-design.md} (Request and
 * completion scenarios; unregistered candidates never block completion).
 */
class UsernameRecoverySessionTest {

    private static final UsernameRecoverySessionId SESSION_ID =
            new UsernameRecoverySessionId("afs_opaque-1");
    private static final UsernameRecoverySession.Scenario SCENARIO =
            UsernameRecoverySession.Scenario.USERNAME_RECOVERY;
    private static final UsernameRecoveryCsrfHash CSRF = new UsernameRecoveryCsrfHash("csrf-hash-1");
    private static final UsernameRecoveryVerifiedDestination DESTINATION =
            new UsernameRecoveryVerifiedDestination(
                    "p****c@example.com", UsernameRecoveryVerifiedDestination.Channel.EMAIL);
    private static final Instant CREATED_AT = Instant.parse("2026-01-01T00:00:00Z");
    private static final Instant EXPIRES_AT = Instant.parse("2026-01-01T00:10:00Z");
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

    private static UsernameRecoverySession session(
            UsernameRecoveryWorkflowStatus status, UsernameRecoverySessionCandidate... candidates) {
        return new UsernameRecoverySession(
                SESSION_ID,
                SCENARIO,
                status,
                new UsernameRecoverySessionRevision(0),
                CSRF,
                DESTINATION,
                Locale.ENGLISH,
                CREATED_AT,
                EXPIRES_AT,
                List.of(candidates));
    }

    private static UsernameRecoverySession activeSingleRegistered() {
        return session(
                UsernameRecoveryWorkflowStatus.ACTIVE,
                registered("sch_1", "DB", "DBA", UsernameRecoveryDeliveryState.AVAILABLE));
    }

    @Test
    void retainsAllProtectedSessionFields() {
        UsernameRecoverySession value = activeSingleRegistered();

        assertEquals(SESSION_ID, value.sessionId());
        assertEquals(SCENARIO, value.scenario());
        assertEquals(UsernameRecoveryWorkflowStatus.ACTIVE, value.status());
        assertEquals(0, value.revision().value());
        assertEquals(CSRF, value.csrfHash());
        assertEquals(DESTINATION, value.destination());
        assertEquals(Locale.ENGLISH, value.language());
        assertEquals(CREATED_AT, value.createdAt());
        assertEquals(EXPIRES_AT, value.expiresAt());
        assertEquals(1, value.candidates().size());
    }

    @Test
    void scenarioUsesEstablishedTypedIdentity() {
        UsernameRecoverySession value = activeSingleRegistered();
        assertEquals(UsernameRecoverySession.Scenario.USERNAME_RECOVERY, value.scenario());
    }

    @Test
    void rejectsNullComponents() {
        UsernameRecoverySessionCandidate candidate =
                registered("sch_1", "DB", "DBA", UsernameRecoveryDeliveryState.AVAILABLE);
        List<UsernameRecoverySessionCandidate> candidates = List.of(candidate);
        UsernameRecoverySessionRevision rev0 = new UsernameRecoverySessionRevision(0);
        UsernameRecoveryWorkflowStatus active = UsernameRecoveryWorkflowStatus.ACTIVE;

        assertThrows(NullPointerException.class, () -> new UsernameRecoverySession(
                null, SCENARIO, active, rev0, CSRF, DESTINATION, Locale.ENGLISH, CREATED_AT, EXPIRES_AT, candidates));
        assertThrows(NullPointerException.class, () -> new UsernameRecoverySession(
                SESSION_ID, null, active, rev0, CSRF, DESTINATION, Locale.ENGLISH, CREATED_AT, EXPIRES_AT, candidates));
        assertThrows(NullPointerException.class, () -> new UsernameRecoverySession(
                SESSION_ID, SCENARIO, null, rev0, CSRF, DESTINATION, Locale.ENGLISH, CREATED_AT, EXPIRES_AT, candidates));
        assertThrows(NullPointerException.class, () -> new UsernameRecoverySession(
                SESSION_ID, SCENARIO, active, null, CSRF, DESTINATION, Locale.ENGLISH, CREATED_AT, EXPIRES_AT, candidates));
        assertThrows(NullPointerException.class, () -> new UsernameRecoverySession(
                SESSION_ID, SCENARIO, active, rev0, null, DESTINATION, Locale.ENGLISH, CREATED_AT, EXPIRES_AT, candidates));
        assertThrows(NullPointerException.class, () -> new UsernameRecoverySession(
                SESSION_ID, SCENARIO, active, rev0, CSRF, null, Locale.ENGLISH, CREATED_AT, EXPIRES_AT, candidates));
        assertThrows(NullPointerException.class, () -> new UsernameRecoverySession(
                SESSION_ID, SCENARIO, active, rev0, CSRF, DESTINATION, null, CREATED_AT, EXPIRES_AT, candidates));
        assertThrows(NullPointerException.class, () -> new UsernameRecoverySession(
                SESSION_ID, SCENARIO, active, rev0, CSRF, DESTINATION, Locale.ENGLISH, null, EXPIRES_AT, candidates));
        assertThrows(NullPointerException.class, () -> new UsernameRecoverySession(
                SESSION_ID, SCENARIO, active, rev0, CSRF, DESTINATION, Locale.ENGLISH, CREATED_AT, null, candidates));
    }

    @Test
    void rejectsExpiryNotStrictlyAfterCreation() {
        UsernameRecoverySessionCandidate candidate =
                registered("sch_1", "DB", "DBA", UsernameRecoveryDeliveryState.AVAILABLE);
        List<UsernameRecoverySessionCandidate> candidates = List.of(candidate);
        UsernameRecoverySessionRevision rev0 = new UsernameRecoverySessionRevision(0);

        assertThrows(IllegalArgumentException.class, () -> new UsernameRecoverySession(
                SESSION_ID, SCENARIO, UsernameRecoveryWorkflowStatus.ACTIVE, rev0, CSRF, DESTINATION,
                Locale.ENGLISH, CREATED_AT, CREATED_AT, candidates));
        assertThrows(IllegalArgumentException.class, () -> new UsernameRecoverySession(
                SESSION_ID, SCENARIO, UsernameRecoveryWorkflowStatus.ACTIVE, rev0, CSRF, DESTINATION,
                Locale.ENGLISH, EXPIRES_AT, CREATED_AT, candidates));
    }

    @Test
    void requiresAtLeastOneCandidate() {
        assertThrows(IllegalArgumentException.class,
                () -> session(UsernameRecoveryWorkflowStatus.ACTIVE));
    }

    @Test
    void requiresAtLeastOneRegisteredCandidate() {
        assertThrows(IllegalArgumentException.class, () -> session(
                UsernameRecoveryWorkflowStatus.ACTIVE,
                unregistered("sch_9", "DB", "DBA", UsernameRecoveryDeliveryState.AVAILABLE)));
    }

    @Test
    void allowsUnregisteredCandidatesWithSessionBoundSchemeRefs() {
        UsernameRecoverySession value = session(
                UsernameRecoveryWorkflowStatus.ACTIVE,
                registered("sch_1", "DB", "DBA", UsernameRecoveryDeliveryState.AVAILABLE),
                unregistered("sch_9", "HK", "HKA", UsernameRecoveryDeliveryState.AVAILABLE));

        assertEquals(2, value.candidates().size());
        assertTrue(value.candidates().stream().allMatch(c -> c.schemeRef() != null));
    }

    @Test
    void rejectsDuplicateSchemeRefs() {
        assertThrows(IllegalArgumentException.class, () -> session(
                UsernameRecoveryWorkflowStatus.ACTIVE,
                registered("sch_1", "DB", "DBA", UsernameRecoveryDeliveryState.AVAILABLE),
                registered("sch_1", "HK", "HKA", UsernameRecoveryDeliveryState.AVAILABLE)));
    }

    @Test
    void rejectsDuplicateAccountEnvTrustCodePairs() {
        assertThrows(IllegalArgumentException.class, () -> session(
                UsernameRecoveryWorkflowStatus.ACTIVE,
                registered("sch_1", "DB", "DBA", UsernameRecoveryDeliveryState.AVAILABLE),
                registered("sch_2", "DB", "DBA", UsernameRecoveryDeliveryState.AVAILABLE)));
    }

    @Test
    void completedStatusRequiresEveryRegisteredCandidateRequested() {
        assertThrows(IllegalArgumentException.class, () -> session(
                UsernameRecoveryWorkflowStatus.COMPLETED,
                registered("sch_1", "DB", "DBA", UsernameRecoveryDeliveryState.AVAILABLE)));
    }

    @Test
    void allRegisteredRequestedRequiresCompletedStatus() {
        assertThrows(IllegalArgumentException.class, () -> session(
                UsernameRecoveryWorkflowStatus.ACTIVE,
                requestedRegistered("sch_1", "DB", "DBA")));
    }

    @Test
    void candidateListIsDefensivelyImmutable() {
        List<UsernameRecoverySessionCandidate> mutable = new ArrayList<>();
        mutable.add(registered("sch_1", "DB", "DBA", UsernameRecoveryDeliveryState.AVAILABLE));
        UsernameRecoverySession value = new UsernameRecoverySession(
                SESSION_ID, SCENARIO, UsernameRecoveryWorkflowStatus.ACTIVE,
                new UsernameRecoverySessionRevision(0), CSRF, DESTINATION, Locale.ENGLISH,
                CREATED_AT, EXPIRES_AT, mutable);

        mutable.add(registered("sch_2", "HK", "HKA", UsernameRecoveryDeliveryState.AVAILABLE));

        assertEquals(1, value.candidates().size());
        assertThrows(UnsupportedOperationException.class,
                () -> value.candidates().add(registered("sch_3", "HK", "HKA", UsernameRecoveryDeliveryState.AVAILABLE)));
    }

    @Test
    void reserveAvailableCandidateProducesReservedAndAdvancesRevision() {
        UsernameRecoverySession reserved =
                activeSingleRegistered().reserve(new UsernameRecoverySchemeRef("sch_1"));

        assertEquals(UsernameRecoveryWorkflowStatus.ACTIVE, reserved.status());
        assertEquals(1, reserved.revision().value());
        assertEquals(
                UsernameRecoveryDeliveryState.RESERVED,
                reserved.candidates().get(0).deliveryState());
    }

    @Test
    void reserveRejectsUnknownSchemeRef() {
        assertThrows(IllegalArgumentException.class,
                () -> activeSingleRegistered().reserve(new UsernameRecoverySchemeRef("unknown")));
    }

    @Test
    void reserveRejectsNullSchemeRef() {
        assertThrows(NullPointerException.class, () -> activeSingleRegistered().reserve(null));
    }

    @Test
    void reserveRejectsAlreadyReservedCandidate() {
        UsernameRecoverySession reserved =
                activeSingleRegistered().reserve(new UsernameRecoverySchemeRef("sch_1"));

        assertThrows(IllegalStateException.class,
                () -> reserved.reserve(new UsernameRecoverySchemeRef("sch_1")));
    }

    @Test
    void reserveRejectsUnregisteredCandidate() {
        UsernameRecoverySession value = session(
                UsernameRecoveryWorkflowStatus.ACTIVE,
                registered("sch_1", "DB", "DBA", UsernameRecoveryDeliveryState.AVAILABLE),
                unregistered("sch_9", "HK", "HKA", UsernameRecoveryDeliveryState.AVAILABLE));

        assertThrows(IllegalStateException.class,
                () -> value.reserve(new UsernameRecoverySchemeRef("sch_9")));
    }

    @Test
    void reserveRejectsTerminalSessions() {
        UsernameRecoverySchemeRef scheme = new UsernameRecoverySchemeRef("sch_1");
        UsernameRecoverySession completed = activeSingleRegistered()
                .reserve(scheme)
                .markRequested(scheme, "sender-ref-1", REQUESTED_AT);
        UsernameRecoverySession cancelled = activeSingleRegistered().cancel();
        UsernameRecoverySession expired = activeSingleRegistered().expire();

        assertThrows(IllegalStateException.class, () -> completed.reserve(scheme));
        assertThrows(IllegalStateException.class, () -> cancelled.reserve(scheme));
        assertThrows(IllegalStateException.class, () -> expired.reserve(scheme));
    }

    @Test
    void markRequestedFromReservedProducesRequestedWithMetadataAndRevision() {
        UsernameRecoverySession reserved =
                activeSingleRegistered().reserve(new UsernameRecoverySchemeRef("sch_1"));
        UsernameRecoverySession requested = reserved.markRequested(
                new UsernameRecoverySchemeRef("sch_1"), "sender-ref-1", REQUESTED_AT);

        assertEquals(UsernameRecoveryWorkflowStatus.COMPLETED, requested.status());
        assertEquals(2, requested.revision().value());
        assertEquals(
                UsernameRecoveryDeliveryState.REQUESTED,
                requested.candidates().get(0).deliveryState());
        assertEquals(REQUESTED_AT, requested.candidates().get(0).requestedAt());
        assertEquals("sender-ref-1", requested.candidates().get(0).senderReference());
    }

    @Test
    void markRequestedRequiresReservedState() {
        assertThrows(IllegalStateException.class, () -> activeSingleRegistered()
                .markRequested(new UsernameRecoverySchemeRef("sch_1"), "sender-ref-1", REQUESTED_AT));
    }

    @Test
    void markRequestedRejectsUnknownSchemeRef() {
        UsernameRecoverySession reserved =
                activeSingleRegistered().reserve(new UsernameRecoverySchemeRef("sch_1"));

        assertThrows(IllegalArgumentException.class, () -> reserved.markRequested(
                new UsernameRecoverySchemeRef("unknown"), "sender-ref-1", REQUESTED_AT));
    }

    @Test
    void markRequestedRejectsNullSenderReferenceOrTimestamp() {
        UsernameRecoverySession reserved =
                activeSingleRegistered().reserve(new UsernameRecoverySchemeRef("sch_1"));
        UsernameRecoverySchemeRef scheme = new UsernameRecoverySchemeRef("sch_1");

        assertThrows(NullPointerException.class,
                () -> reserved.markRequested(scheme, null, REQUESTED_AT));
        assertThrows(NullPointerException.class,
                () -> reserved.markRequested(scheme, "sender-ref-1", null));
    }

    @Test
    void markRequestedRejectsUnregisteredCandidate() {
        UsernameRecoverySession value = session(
                UsernameRecoveryWorkflowStatus.ACTIVE,
                registered("sch_1", "DB", "DBA", UsernameRecoveryDeliveryState.AVAILABLE),
                unregistered("sch_9", "HK", "HKA", UsernameRecoveryDeliveryState.AVAILABLE));

        assertThrows(IllegalStateException.class, () -> value.markRequested(
                new UsernameRecoverySchemeRef("sch_9"), "sender-ref-1", REQUESTED_AT));
    }

    @Test
    void markRequestedRejectsTerminalSession() {
        UsernameRecoverySchemeRef scheme = new UsernameRecoverySchemeRef("sch_1");
        UsernameRecoverySession completed = activeSingleRegistered()
                .reserve(scheme)
                .markRequested(scheme, "sender-ref-1", REQUESTED_AT);

        assertThrows(IllegalStateException.class, () -> completed.markRequested(
                scheme, "sender-ref-2", REQUESTED_AT));
    }

    @Test
    void firstSuccessfulRequestMakesMultiRegisteredSessionPartiallyRequested() {
        UsernameRecoverySession value = session(
                UsernameRecoveryWorkflowStatus.ACTIVE,
                registered("sch_1", "DB", "DBA", UsernameRecoveryDeliveryState.AVAILABLE),
                registered("sch_2", "HK", "HKA", UsernameRecoveryDeliveryState.AVAILABLE));
        UsernameRecoverySession requested = value
                .reserve(new UsernameRecoverySchemeRef("sch_1"))
                .markRequested(new UsernameRecoverySchemeRef("sch_1"), "sender-ref-1", REQUESTED_AT);

        assertEquals(UsernameRecoveryWorkflowStatus.PARTIALLY_REQUESTED, requested.status());
        assertFalse(requested.isTerminal());
        assertEquals(
                UsernameRecoveryDeliveryState.REQUESTED,
                requested.candidates().get(0).deliveryState());
        assertEquals(
                UsernameRecoveryDeliveryState.AVAILABLE,
                requested.candidates().get(1).deliveryState());
    }

    @Test
    void finalSuccessfulRequestCompletesSession() {
        UsernameRecoverySession value = session(
                UsernameRecoveryWorkflowStatus.ACTIVE,
                registered("sch_1", "DB", "DBA", UsernameRecoveryDeliveryState.AVAILABLE),
                registered("sch_2", "HK", "HKA", UsernameRecoveryDeliveryState.AVAILABLE));
        UsernameRecoverySession completed = value
                .reserve(new UsernameRecoverySchemeRef("sch_1"))
                .markRequested(new UsernameRecoverySchemeRef("sch_1"), "sender-ref-1", REQUESTED_AT)
                .reserve(new UsernameRecoverySchemeRef("sch_2"))
                .markRequested(new UsernameRecoverySchemeRef("sch_2"), "sender-ref-2", REQUESTED_AT);

        assertEquals(UsernameRecoveryWorkflowStatus.COMPLETED, completed.status());
        assertEquals(4, completed.revision().value());
        assertTrue(completed.candidates().stream()
                .filter(UsernameRecoverySessionCandidate::registered)
                .allMatch(c -> c.deliveryState() == UsernameRecoveryDeliveryState.REQUESTED));
    }

    @Test
    void unregisteredCandidatesDoNotBlockCompletion() {
        UsernameRecoverySession value = session(
                UsernameRecoveryWorkflowStatus.ACTIVE,
                registered("sch_1", "DB", "DBA", UsernameRecoveryDeliveryState.AVAILABLE),
                unregistered("sch_9", "HK", "HKA", UsernameRecoveryDeliveryState.AVAILABLE));
        UsernameRecoverySession completed = value
                .reserve(new UsernameRecoverySchemeRef("sch_1"))
                .markRequested(new UsernameRecoverySchemeRef("sch_1"), "sender-ref-1", REQUESTED_AT);

        assertEquals(UsernameRecoveryWorkflowStatus.COMPLETED, completed.status());
        assertEquals(
                UsernameRecoveryDeliveryState.AVAILABLE,
                completed.candidates().get(1).deliveryState());
    }

    @Test
    void markFailedFromReservedProducesFailedAndKeepsSessionStatus() {
        UsernameRecoverySession failed = activeSingleRegistered()
                .reserve(new UsernameRecoverySchemeRef("sch_1"))
                .markFailed(new UsernameRecoverySchemeRef("sch_1"));

        assertEquals(UsernameRecoveryDeliveryState.FAILED, failed.candidates().get(0).deliveryState());
        assertEquals(UsernameRecoveryWorkflowStatus.ACTIVE, failed.status());
        assertEquals(2, failed.revision().value());
    }

    @Test
    void markFailedRequiresReservedState() {
        assertThrows(IllegalStateException.class,
                () -> activeSingleRegistered().markFailed(new UsernameRecoverySchemeRef("sch_1")));
    }

    @Test
    void markFailedRejectsUnregisteredCandidate() {
        UsernameRecoverySession value = session(
                UsernameRecoveryWorkflowStatus.ACTIVE,
                registered("sch_1", "DB", "DBA", UsernameRecoveryDeliveryState.AVAILABLE),
                unregistered("sch_9", "HK", "HKA", UsernameRecoveryDeliveryState.AVAILABLE));

        assertThrows(IllegalStateException.class,
                () -> value.markFailed(new UsernameRecoverySchemeRef("sch_9")));
    }

    @Test
    void markUnknownOutcomeFromReservedProducesUnknownOutcome() {
        UsernameRecoverySession unknown = activeSingleRegistered()
                .reserve(new UsernameRecoverySchemeRef("sch_1"))
                .markUnknownOutcome(new UsernameRecoverySchemeRef("sch_1"));

        assertEquals(
                UsernameRecoveryDeliveryState.UNKNOWN_OUTCOME,
                unknown.candidates().get(0).deliveryState());
        assertEquals(UsernameRecoveryWorkflowStatus.ACTIVE, unknown.status());
        assertEquals(2, unknown.revision().value());
    }

    @Test
    void markUnknownOutcomeRequiresReservedState() {
        assertThrows(IllegalStateException.class,
                () -> activeSingleRegistered().markUnknownOutcome(new UsernameRecoverySchemeRef("sch_1")));
    }

    @Test
    void failedCandidateDoesNotCountTowardCompletion() {
        UsernameRecoverySession value = session(
                UsernameRecoveryWorkflowStatus.ACTIVE,
                registered("sch_1", "DB", "DBA", UsernameRecoveryDeliveryState.AVAILABLE),
                registered("sch_2", "HK", "HKA", UsernameRecoveryDeliveryState.AVAILABLE));
        UsernameRecoverySession partial = value
                .reserve(new UsernameRecoverySchemeRef("sch_1"))
                .markRequested(new UsernameRecoverySchemeRef("sch_1"), "sender-ref-1", REQUESTED_AT)
                .reserve(new UsernameRecoverySchemeRef("sch_2"))
                .markFailed(new UsernameRecoverySchemeRef("sch_2"));

        assertEquals(UsernameRecoveryWorkflowStatus.PARTIALLY_REQUESTED, partial.status());
        assertEquals(UsernameRecoveryDeliveryState.FAILED, partial.candidates().get(1).deliveryState());
    }

    @Test
    void cancelFromActiveProducesCancelledAndAdvancesRevision() {
        UsernameRecoverySession cancelled = activeSingleRegistered().cancel();

        assertEquals(UsernameRecoveryWorkflowStatus.CANCELLED, cancelled.status());
        assertEquals(1, cancelled.revision().value());
        assertTrue(cancelled.isTerminal());
    }

    @Test
    void cancelFromPartiallyRequestedProducesCancelled() {
        UsernameRecoverySession value = session(
                UsernameRecoveryWorkflowStatus.ACTIVE,
                registered("sch_1", "DB", "DBA", UsernameRecoveryDeliveryState.AVAILABLE),
                registered("sch_2", "HK", "HKA", UsernameRecoveryDeliveryState.AVAILABLE));
        UsernameRecoverySession partial = value
                .reserve(new UsernameRecoverySchemeRef("sch_1"))
                .markRequested(new UsernameRecoverySchemeRef("sch_1"), "sender-ref-1", REQUESTED_AT);

        assertEquals(UsernameRecoveryWorkflowStatus.CANCELLED, partial.cancel().status());
    }

    @Test
    void cancelAndExpireRejectTerminalSessions() {
        UsernameRecoverySchemeRef scheme = new UsernameRecoverySchemeRef("sch_1");
        UsernameRecoverySession completed = activeSingleRegistered()
                .reserve(scheme)
                .markRequested(scheme, "sender-ref-1", REQUESTED_AT);

        assertThrows(IllegalStateException.class, completed::cancel);
        assertThrows(IllegalStateException.class, completed::expire);
    }

    @Test
    void expireFromActiveProducesExpiredAndAdvancesRevision() {
        UsernameRecoverySession expired = activeSingleRegistered().expire();

        assertEquals(UsernameRecoveryWorkflowStatus.EXPIRED, expired.status());
        assertEquals(1, expired.revision().value());
        assertTrue(expired.isTerminal());
    }

    @Test
    void isExpiredUsesExpiryBoundary() {
        UsernameRecoverySession value = activeSingleRegistered();

        assertFalse(value.isExpired(Instant.parse("2026-01-01T00:09:59Z")));
        assertTrue(value.isExpired(EXPIRES_AT));
        assertTrue(value.isExpired(Instant.parse("2026-01-01T00:10:01Z")));
    }

    @Test
    void isTerminalReflectsStatus() {
        UsernameRecoverySchemeRef scheme = new UsernameRecoverySchemeRef("sch_1");
        UsernameRecoverySession completed = activeSingleRegistered()
                .reserve(scheme)
                .markRequested(scheme, "sender-ref-1", REQUESTED_AT);

        assertFalse(activeSingleRegistered().isTerminal());
        assertTrue(completed.isTerminal());
        assertTrue(activeSingleRegistered().cancel().isTerminal());
        assertTrue(activeSingleRegistered().expire().isTerminal());
    }

    @Test
    void toStringRedactsSessionIdCsrfDestinationAndCandidateSensitiveValues() {
        UsernameRecoverySession value = activeSingleRegistered();

        assertEquals(
                "UsernameRecoverySession[sessionId=<redacted>, scenario=USERNAME_RECOVERY, status=ACTIVE,"
                        + " revision=0, csrfHash=<redacted>, destination=<redacted>, language=en,"
                        + " createdAt=2026-01-01T00:00:00Z, expiresAt=2026-01-01T00:10:00Z,"
                        + " candidateCount=1]",
                value.toString());
    }

    @Test
    void toStringNeverDisclosesUsernameSchemeRefOrSenderReference() {
        UsernameRecoverySession value = session(
                UsernameRecoveryWorkflowStatus.ACTIVE,
                registered("sch_1", "DB", "DBA", UsernameRecoveryDeliveryState.AVAILABLE),
                unregistered("sch_9", "HK", "HKA", UsernameRecoveryDeliveryState.AVAILABLE));

        String text = value.toString();
        assertFalse(text.contains("user-"));
        assertFalse(text.contains("sch_1"));
        assertFalse(text.contains("sch_9"));
        assertFalse(text.contains("sender-ref"));
        assertFalse(text.contains("afs_opaque"));
    }

    @Test
    void revisionAdvancesExactlyOncePerApprovedMutation() {
        UsernameRecoverySession value = session(
                UsernameRecoveryWorkflowStatus.ACTIVE,
                registered("sch_1", "DB", "DBA", UsernameRecoveryDeliveryState.AVAILABLE),
                registered("sch_2", "HK", "HKA", UsernameRecoveryDeliveryState.AVAILABLE));

        UsernameRecoverySession r1 = value.reserve(new UsernameRecoverySchemeRef("sch_1"));
        assertEquals(1, r1.revision().value());
        UsernameRecoverySession r2 = r1.markRequested(
                new UsernameRecoverySchemeRef("sch_1"), "sender-ref-1", REQUESTED_AT);
        assertEquals(2, r2.revision().value());
        UsernameRecoverySession r3 = r2.reserve(new UsernameRecoverySchemeRef("sch_2"));
        assertEquals(3, r3.revision().value());
        UsernameRecoverySession r4 = r3.markRequested(
                new UsernameRecoverySchemeRef("sch_2"), "sender-ref-2", REQUESTED_AT);
        assertEquals(4, r4.revision().value());
    }

    @Test
    void concurrentMutationsFromSameSnapshotProduceSameRevisionRequiringPersistenceCas() {
        UsernameRecoverySession value = session(
                UsernameRecoveryWorkflowStatus.ACTIVE,
                registered("sch_1", "DB", "DBA", UsernameRecoveryDeliveryState.AVAILABLE),
                registered("sch_2", "HK", "HKA", UsernameRecoveryDeliveryState.AVAILABLE));

        UsernameRecoverySession a = value.reserve(new UsernameRecoverySchemeRef("sch_1"));
        UsernameRecoverySession b = value.reserve(new UsernameRecoverySchemeRef("sch_2"));

        assertEquals(1, a.revision().value());
        assertEquals(1, b.revision().value());
        assertEquals(UsernameRecoveryDeliveryState.RESERVED, a.candidates().get(0).deliveryState());
        assertEquals(UsernameRecoveryDeliveryState.RESERVED, b.candidates().get(1).deliveryState());
        // Both derived sessions share revision 1 from base revision 0; the domain cannot detect the
        // race, so the persistence boundary must compare-and-swap the expected revision (Task 011/012).
    }
}
