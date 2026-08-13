package com.bct.ngtpa.apiservice.application.usecase.auth.usernamerecovery;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.application.dto.auth.usernamerecovery.UsernameRecoveryCancelCommand;
import com.bct.ngtpa.apiservice.application.dto.auth.usernamerecovery.UsernameRecoveryCancelResult;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.*;
import com.bct.ngtpa.apiservice.application.dto.session.usernamerecovery.UsernameRecoveryWorkflowState;
import com.bct.ngtpa.apiservice.application.dto.session.usernamerecovery.UsernameRecoveryWorkflowStateRevision;
import com.bct.ngtpa.apiservice.application.port.out.*;
import com.bct.ngtpa.apiservice.application.usecase.session.authflow.CsrfTokenValidationService;
import com.bct.ngtpa.apiservice.application.usecase.session.authflow.Sha256CsrfTokenHasher;
import com.bct.ngtpa.apiservice.domain.model.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class CancelUsernameRecoveryServiceTest {
    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final AuthFlowSessionId ID = new AuthFlowSessionId("afs_opaque-1");
    private static final String KEY = "idem_12345678";
    private static final String CSRF = "csrf-token-abc";
    private final List<String> calls = new ArrayList<>();
    private final FakeIdempotencyPort idempotency = new FakeIdempotencyPort(calls);
    private final FakeAuthFlowPort envelopes = new FakeAuthFlowPort(calls);
    private final FakeWorkflowPort workflows = new FakeWorkflowPort(calls);
    private CancelUsernameRecoveryService service;

    @BeforeEach
    void setUp() {
        envelopes.snapshot = envelope(AuthFlowStatus.STARTED, NOW.plusSeconds(600));
        workflows.state = workflow(UsernameRecoveryWorkflowStatus.ACTIVE, 0);
        service = new CancelUsernameRecoveryService(
                new UsernameRecoveryCancelFingerprintGenerator(), idempotency, envelopes, workflows,
                new CsrfTokenValidationService(new Sha256CsrfTokenHasher()), List.of(), CLOCK, 600);
    }

    @Test
    void liveCancellationRetainsBothRecordsAndPreservesExpiry() {
        Instant expiry = envelopes.snapshot.session().expiresAt();
        StepVerifier.create(service.cancel(command(), CSRF, null, null))
                .expectNext(new UsernameRecoveryCancelResult(UsernameRecoveryCancelResult.Outcome.SUCCESS))
                .verifyComplete();
        assertThat(calls).containsSubsequence("idem.reserve", "envelope.load", "envelope.cancel",
                "workflow.load", "workflow.update", "idem.complete");
        assertThat(envelopes.snapshot.session().status()).isEqualTo(AuthFlowStatus.CANCELLED);
        assertThat(envelopes.snapshot.session().expiresAt()).isEqualTo(expiry);
        assertThat(workflows.state.status()).isEqualTo(UsernameRecoveryWorkflowStatus.CANCELLED);
        assertThat(workflows.state.revision().value()).isEqualTo(1);
        assertThat(envelopes.deleteCalls).isZero();
        assertThat(workflows.deleteCalls).isZero();
    }

    @Test
    void alreadyCancelledEnvelopeReconcilesActiveSidecarWithoutSecondEnvelopeMutation() {
        envelopes.snapshot = envelope(AuthFlowStatus.CANCELLED, NOW.plusSeconds(600));
        StepVerifier.create(service.cancel(command(), null, null, null)).expectNextCount(1).verifyComplete();
        assertThat(envelopes.cancelCalls).isZero();
        assertThat(workflows.state.status()).isEqualTo(UsernameRecoveryWorkflowStatus.CANCELLED);
        assertThat(workflows.updateCalls).isEqualTo(1);
    }

    @Test
    void alreadyCancelledSidecarDoesNotAdvanceRevisionAgain() {
        envelopes.snapshot = envelope(AuthFlowStatus.CANCELLED, NOW.plusSeconds(600));
        workflows.state = workflow(UsernameRecoveryWorkflowStatus.CANCELLED, 1);
        StepVerifier.create(service.cancel(command(), null, null, null)).expectNextCount(1).verifyComplete();
        assertThat(workflows.updateCalls).isZero();
        assertThat(workflows.state.revision().value()).isEqualTo(1);
    }

    @Test
    void liveEnvelopeRequiresCsrfAndOriginBeforeAnyMutation() {
        StepVerifier.create(service.cancel(command(), "wrong", null, null))
                .expectError(UsernameRecoveryCancelException.class).verify();
        assertThat(envelopes.cancelCalls).isZero();
        assertThat(workflows.updateCalls).isZero();
        assertThat(envelopes.deleteCalls).isZero();
        assertThat(workflows.deleteCalls).isZero();
    }

    @Test
    void absentEnvelopeSettlesWithoutMutatingOrphanSidecar() {
        envelopes.snapshot = null;
        StepVerifier.create(service.cancel(command(), null, null, null)).expectNextCount(1).verifyComplete();
        assertThat(workflows.loadCalls).isZero();
        assertThat(workflows.updateCalls).isZero();
    }

    @Test
    void envelopeConflictRecordsStableUnknownOutcome() {
        envelopes.cancelOutcome = AuthFlowSessionMutationOutcome.SESSION_REVISION_CHANGED;
        StepVerifier.create(service.cancel(command(), CSRF, null, null))
                .expectNext(new UsernameRecoveryCancelResult(UsernameRecoveryCancelResult.Outcome.SYSTEM_FAILED))
                .verifyComplete();
        assertThat(idempotency.unknownCalls).isEqualTo(1);
        assertThat(workflows.updateCalls).isZero();
    }

    @Test
    void sidecarConflictRecordsStableUnknownOutcomeAndKeepsEnvelopeCancelled() {
        workflows.updateOutcome = UsernameRecoveryWorkflowStateMutationOutcome.REVISION_CONFLICT;
        StepVerifier.create(service.cancel(command(), CSRF, null, null))
                .expectNext(new UsernameRecoveryCancelResult(UsernameRecoveryCancelResult.Outcome.SYSTEM_FAILED))
                .verifyComplete();
        assertThat(envelopes.snapshot.session().status()).isEqualTo(AuthFlowStatus.CANCELLED);
        assertThat(idempotency.unknownCalls).isEqualTo(1);
    }

    @Test
    void completedReplayDoesNotLoadOrMutateRecords() {
        idempotency.reserveOutcome = UsernameRecoveryIdempotencyOutcome.COMPLETED_REPLAY;
        idempotency.record = completedRecord();
        StepVerifier.create(service.cancel(command(), null, null, null)).expectNextCount(1).verifyComplete();
        assertThat(envelopes.loadCalls).isZero();
        assertThat(workflows.loadCalls).isZero();
    }

    @Test
    void portCompatibilityMethodFailsClosed() {
        StepVerifier.create(service.cancel(command())).expectError(UsernameRecoveryCancelException.class).verify();
        assertThat(calls).isEmpty();
    }

    private static UsernameRecoveryCancelCommand command() {
        return new UsernameRecoveryCancelCommand(ID, KEY);
    }

    private static AuthFlowSessionSnapshot envelope(AuthFlowStatus status, Instant expiry) {
        String hash = new Sha256CsrfTokenHasher().hash(CSRF);
        return new AuthFlowSessionSnapshot(new AuthFlowSession(
                ID, AuthFlowScenario.USERNAME_RECOVERY, status, hash, null,
                AuthFlowSessionData.empty(), NOW, NOW, expiry),
                new SessionRevision("00000000000000000000000000000001"));
    }

    private static UsernameRecoveryWorkflowState workflow(UsernameRecoveryWorkflowStatus status, int revision) {
        return new UsernameRecoveryWorkflowState(ID, status,
                new UsernameRecoveryWorkflowStateRevision(revision),
                new UsernameRecoveryVerifiedDestination("member@example.com",
                        UsernameRecoveryVerifiedDestination.Channel.EMAIL), Locale.ENGLISH,
                List.of(new UsernameRecoverySessionCandidate(
                        new UsernameRecoveryCandidate(new UsernameRecoverySchemeRef("sch_opaque-1"),
                                "DB", "DBA", true, "user-1", UsernameRecoveryDeliveryState.AVAILABLE),
                        null, null)));
    }

    private static UsernameRecoveryIdempotencyRecord completedRecord() {
        return new UsernameRecoveryIdempotencyRecord(UsernameRecoveryIdempotencyAction.CANCEL,
                new UsernameRecoveryIdempotencyKey(KEY),
                new UsernameRecoveryIdempotencyFingerprint(
                        "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"),
                new UsernameRecoveryIdempotencyOperationReference("op-ref"),
                UsernameRecoveryIdempotencyRecord.Status.COMPLETED,
                UsernameRecoveryCancelReplayCodec.encode(), NOW, NOW, NOW.plusSeconds(600));
    }

    private static final class FakeAuthFlowPort implements AuthFlowSessionPort {
        private final List<String> calls;
        private AuthFlowSessionSnapshot snapshot;
        private AuthFlowSessionMutationOutcome cancelOutcome = AuthFlowSessionMutationOutcome.APPLIED;
        private int loadCalls;
        private int cancelCalls;
        private int deleteCalls;
        FakeAuthFlowPort(List<String> calls) { this.calls = calls; }
        @Override public Mono<AuthFlowSession> create(AuthFlowSessionCreateCommand c) { throw new UnsupportedOperationException(); }
        @Override public Mono<Optional<AuthFlowSession>> load(AuthFlowSessionId id) { return Mono.just(Optional.empty()); }
        @Override public Mono<Optional<AuthFlowSessionSnapshot>> loadSnapshot(AuthFlowSessionId id) {
            calls.add("envelope.load"); loadCalls++; return Mono.just(Optional.ofNullable(snapshot));
        }
        @Override public Mono<AuthFlowSessionMutationResult> cancelRevisioned(AuthFlowSessionSnapshot expected, Instant now) {
            calls.add("envelope.cancel"); cancelCalls++;
            if (cancelOutcome != AuthFlowSessionMutationOutcome.APPLIED) {
                return Mono.just(AuthFlowSessionMutationResult.outcome(cancelOutcome));
            }
            AuthFlowSession s = expected.session();
            AuthFlowSession cancelled = new AuthFlowSession(s.authFlowSessionId(), s.scenario(),
                    AuthFlowStatus.CANCELLED, s.csrfTokenHash(), s.activeOtpChallengeId(), s.data(),
                    s.createdAt(), now, s.expiresAt());
            snapshot = new AuthFlowSessionSnapshot(cancelled,
                    new SessionRevision("11111111111111111111111111111111"));
            return Mono.just(new AuthFlowSessionMutationResult(AuthFlowSessionMutationOutcome.APPLIED, snapshot));
        }
        @Override public Mono<AuthFlowSessionDeleteResult> deleteRevisioned(RevisionedAuthFlowSessionDeleteCommand c) {
            deleteCalls++; throw new AssertionError("normal cancellation must not delete envelope");
        }
    }

    private static final class FakeWorkflowPort implements UsernameRecoveryWorkflowStatePort {
        private final List<String> calls;
        private UsernameRecoveryWorkflowState state;
        private UsernameRecoveryWorkflowStateMutationOutcome updateOutcome = UsernameRecoveryWorkflowStateMutationOutcome.APPLIED;
        private int loadCalls;
        private int updateCalls;
        private int deleteCalls;
        FakeWorkflowPort(List<String> calls) { this.calls = calls; }
        @Override public Mono<UsernameRecoveryWorkflowStateMutationResult> create(UsernameRecoveryWorkflowStateCreateCommand c) { throw new UnsupportedOperationException(); }
        @Override public Mono<UsernameRecoveryWorkflowStateLoadResult> load(AuthFlowSessionId id) {
            calls.add("workflow.load"); loadCalls++;
            return Mono.just(state == null ? UsernameRecoveryWorkflowStateLoadResult.notFound()
                    : UsernameRecoveryWorkflowStateLoadResult.loaded(state));
        }
        @Override public Mono<UsernameRecoveryWorkflowStateMutationResult> update(UsernameRecoveryWorkflowStateMutationCommand c) {
            calls.add("workflow.update"); updateCalls++;
            if (updateOutcome != UsernameRecoveryWorkflowStateMutationOutcome.APPLIED) {
                return Mono.just(UsernameRecoveryWorkflowStateMutationResult.outcome(updateOutcome));
            }
            state = c.updatedState();
            return Mono.just(new UsernameRecoveryWorkflowStateMutationResult(updateOutcome, state));
        }
        @Override public Mono<UsernameRecoveryWorkflowStateDeleteResult> delete(AuthFlowSessionId id) {
            deleteCalls++; throw new AssertionError("normal cancellation must not delete sidecar");
        }
    }

    private static final class FakeIdempotencyPort implements UsernameRecoveryIdempotencyPort {
        private final List<String> calls;
        private UsernameRecoveryIdempotencyOutcome reserveOutcome = UsernameRecoveryIdempotencyOutcome.RESERVED;
        private UsernameRecoveryIdempotencyRecord record;
        private int unknownCalls;
        FakeIdempotencyPort(List<String> calls) { this.calls = calls; }
        @Override public Mono<ReservationResult> reserve(ReserveCommand c) {
            calls.add("idem.reserve");
            if (reserveOutcome == UsernameRecoveryIdempotencyOutcome.RESERVED) {
                return Mono.just(new ReservationResult(reserveOutcome,
                        new UsernameRecoveryIdempotencyReservation(c.action(), c.idempotencyKey(),
                                c.fingerprint(), c.operationReference(), c.expiresAt())));
            }
            return Mono.just(new ReservationResult(reserveOutcome, null));
        }
        @Override public Mono<Optional<UsernameRecoveryIdempotencyRecord>> resolve(UsernameRecoveryIdempotencyAction a, UsernameRecoveryIdempotencyKey k) {
            calls.add("idem.resolve"); return Mono.just(Optional.ofNullable(record));
        }
        @Override public Mono<UsernameRecoveryIdempotencyRecord> complete(CompleteCommand c) {
            calls.add("idem.complete");
            return Mono.just(new UsernameRecoveryIdempotencyRecord(c.action(), c.idempotencyKey(),
                    c.fingerprint(), c.operationReference(), UsernameRecoveryIdempotencyRecord.Status.COMPLETED,
                    c.replaySnapshot(), c.now(), c.now(), c.now().plus(c.ttl())));
        }
        @Override public Mono<UsernameRecoveryIdempotencyRecord> recordUnknownOutcome(UnknownOutcomeCommand c) {
            calls.add("idem.unknown"); unknownCalls++;
            return Mono.just(new UsernameRecoveryIdempotencyRecord(c.action(), c.idempotencyKey(),
                    c.fingerprint(), c.operationReference(), UsernameRecoveryIdempotencyRecord.Status.UNKNOWN_OUTCOME,
                    null, c.now(), c.now(), c.now().plus(c.ttl())));
        }
    }
}
