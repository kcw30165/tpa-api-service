package com.bct.ngtpa.apiservice.application.usecase.auth.usernamerecovery;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.application.dto.auth.usernamerecovery.UsernameRecoveryCancelCommand;
import com.bct.ngtpa.apiservice.application.dto.auth.usernamerecovery.UsernameRecoveryCancelResult;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.*;
import com.bct.ngtpa.apiservice.application.port.out.*;
import com.bct.ngtpa.apiservice.application.usecase.auth.usernamerecovery.UsernameRecoveryCancelException.Reason;
import com.bct.ngtpa.apiservice.application.usecase.session.authflow.Sha256CsrfTokenHasher;
import com.bct.ngtpa.apiservice.application.usecase.session.authflow.CsrfTokenValidationService;
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
    private final List<String> log = new ArrayList<>();
    private final FakeIdempotencyPort idempotency = new FakeIdempotencyPort(log);
    private final FakeAuthFlowPort envelopes = new FakeAuthFlowPort(log);
    private final FakeWorkflowPort workflows = new FakeWorkflowPort(log);
    private CancelUsernameRecoveryService service;

    @BeforeEach
    void setUp() {
        envelopes.snapshot = snapshot(AuthFlowStatus.STARTED, NOW.plusSeconds(600), AuthFlowScenario.USERNAME_RECOVERY, ID);
        service = newService(List.of());
    }

    private CancelUsernameRecoveryService newService(List<String> origins) {
        return new CancelUsernameRecoveryService(
                new UsernameRecoveryCancelFingerprintGenerator(), idempotency, envelopes, workflows,
                new CsrfTokenValidationService(new Sha256CsrfTokenHasher()), origins, CLOCK, 600);
    }

    @Test
    void liveEnvelopeDeletesExactRevisionThenSidecarAndCompletes() {
        StepVerifier.create(service.cancel(command(), CSRF, null, null))
                .assertNext(result -> assertThat(result.outcome()).isEqualTo(UsernameRecoveryCancelResult.Outcome.SUCCESS))
                .verifyComplete();
        assertThat(log).containsSubsequence("idem.reserve", "envelope.load", "envelope.delete", "workflow.delete", "idem.complete");
        assertThat(envelopes.lastDelete.expected()).isSameAs(envelopes.snapshot);
    }

    @Test
    void absentEnvelopeStillDeletesSidecarAndCompletes() {
        envelopes.snapshot = null;
        StepVerifier.create(service.cancel(command(), null, null, null))
                .expectNext(new UsernameRecoveryCancelResult(UsernameRecoveryCancelResult.Outcome.SUCCESS))
                .verifyComplete();
        assertThat(envelopes.deleteCalls).isZero();
        assertThat(workflows.deleteCalls).isEqualTo(1);
    }

    @Test
    void liveEnvelopeRequiresValidCsrfAndPreservesBothRecordsOnFailure() {
        StepVerifier.create(service.cancel(command(), "wrong", null, null))
                .expectErrorSatisfies(error -> assertThat(((UsernameRecoveryCancelException) error).reason()).isEqualTo(Reason.SECURITY_INVALID))
                .verify();
        assertThat(envelopes.deleteCalls).isZero();
        assertThat(workflows.deleteCalls).isZero();
    }

    @Test
    void disallowedOriginPreservesBothRecords() {
        service = newService(List.of("https://app.example"));
        StepVerifier.create(service.cancel(command(), CSRF, "https://evil.example", null))
                .expectError(UsernameRecoveryCancelException.class).verify();
        assertThat(envelopes.deleteCalls).isZero();
        assertThat(workflows.deleteCalls).isZero();
    }

    @Test
    void expiredAndTerminalEnvelopesSkipCsrfGate() {
        envelopes.snapshot = snapshot(AuthFlowStatus.STARTED, NOW.minusSeconds(1), AuthFlowScenario.USERNAME_RECOVERY, ID);
        StepVerifier.create(service.cancel(command(), null, null, null)).expectNextCount(1).verifyComplete();
        setUp();
        envelopes.snapshot = snapshot(AuthFlowStatus.COMPLETED, NOW.plusSeconds(600), AuthFlowScenario.USERNAME_RECOVERY, ID);
        StepVerifier.create(service.cancel(command(), null, null, null)).expectNextCount(1).verifyComplete();
    }

    @Test
    void wrongScenarioOrIdentityFailsClosedWithoutDeletion() {
        envelopes.snapshot = snapshot(AuthFlowStatus.STARTED, NOW.plusSeconds(600), AuthFlowScenario.LOGIN, ID);
        StepVerifier.create(service.cancel(command(), CSRF, null, null)).expectError(UsernameRecoveryCancelException.class).verify();
        envelopes.snapshot = snapshot(AuthFlowStatus.STARTED, NOW.plusSeconds(600), AuthFlowScenario.USERNAME_RECOVERY, new AuthFlowSessionId("afs_other"));
        StepVerifier.create(service.cancel(command(), CSRF, null, null)).expectError(UsernameRecoveryCancelException.class).verify();
        assertThat(envelopes.deleteCalls).isZero();
    }

    @Test
    void envelopeRevisionConflictRecordsStableUnknownOutcomeWithoutRetry() {
        envelopes.deleteOutcome = AuthFlowSessionMutationOutcome.SESSION_REVISION_CHANGED;
        StepVerifier.create(service.cancel(command(), CSRF, null, null))
                .expectNext(new UsernameRecoveryCancelResult(UsernameRecoveryCancelResult.Outcome.SYSTEM_FAILED)).verifyComplete();
        assertThat(envelopes.deleteCalls).isEqualTo(1);
        assertThat(idempotency.unknownCalls).isEqualTo(1);
    }

    @Test
    void sidecarDeleteFailureRecordsStableUnknownOutcome() {
        workflows.deleteOutcome = UsernameRecoveryWorkflowStateMutationOutcome.SYSTEM_FAILURE;
        StepVerifier.create(service.cancel(command(), CSRF, null, null))
                .expectNext(new UsernameRecoveryCancelResult(UsernameRecoveryCancelResult.Outcome.SYSTEM_FAILED)).verifyComplete();
        assertThat(workflows.deleteCalls).isEqualTo(1);
        assertThat(idempotency.completeCalls).isZero();
        assertThat(idempotency.unknownCalls).isEqualTo(1);
    }

    @Test
    void completedReplayDoesNotLoadOrDeleteEitherRecord() {
        idempotency.reserveOutcome = UsernameRecoveryIdempotencyOutcome.COMPLETED_REPLAY;
        idempotency.record = completedRecord();
        StepVerifier.create(service.cancel(command(), null, null, null))
                .expectNext(new UsernameRecoveryCancelResult(UsernameRecoveryCancelResult.Outcome.SUCCESS)).verifyComplete();
        assertThat(envelopes.loadCalls).isZero();
        assertThat(envelopes.deleteCalls).isZero();
        assertThat(workflows.deleteCalls).isZero();
    }

    @Test
    void malformedReplayFailsClosedWithoutDeletion() {
        idempotency.reserveOutcome = UsernameRecoveryIdempotencyOutcome.COMPLETED_REPLAY;
        idempotency.record = completedRecord(new UsernameRecoveryIdempotencyRecord.ReplaySnapshot("bad"));
        StepVerifier.create(service.cancel(command(), null, null, null)).expectError(UsernameRecoveryCancelException.class).verify();
        assertThat(envelopes.loadCalls).isZero();
    }

    @Test
    void portCompatibilityMethodFailsClosed() {
        StepVerifier.create(service.cancel(command()))
                .expectErrorSatisfies(error -> assertThat(((UsernameRecoveryCancelException) error).reason()).isEqualTo(Reason.SECURITY_CONTEXT_REQUIRED))
                .verify();
        assertThat(log).isEmpty();
    }

    private static UsernameRecoveryCancelCommand command() { return new UsernameRecoveryCancelCommand(ID, KEY); }

    private static AuthFlowSessionSnapshot snapshot(AuthFlowStatus status, Instant expiry, AuthFlowScenario scenario, AuthFlowSessionId id) {
        String hash = new Sha256CsrfTokenHasher().hash(CSRF);
        return new AuthFlowSessionSnapshot(new AuthFlowSession(id, scenario, status, hash, null,
                AuthFlowSessionData.empty(), NOW, NOW, expiry), new SessionRevision("00000000000000000000000000000001"));
    }

    private static UsernameRecoveryIdempotencyRecord completedRecord() {
        return completedRecord(UsernameRecoveryCancelReplayCodec.encode());
    }

    private static UsernameRecoveryIdempotencyRecord completedRecord(UsernameRecoveryIdempotencyRecord.ReplaySnapshot snapshot) {
        return new UsernameRecoveryIdempotencyRecord(UsernameRecoveryIdempotencyAction.CANCEL,
                new UsernameRecoveryIdempotencyKey(KEY),
                new UsernameRecoveryIdempotencyFingerprint("0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"),
                new UsernameRecoveryIdempotencyOperationReference("op-ref"),
                UsernameRecoveryIdempotencyRecord.Status.COMPLETED, snapshot, NOW, NOW, NOW.plusSeconds(600));
    }

    private static final class FakeAuthFlowPort implements AuthFlowSessionPort {
        private final List<String> log;
        private AuthFlowSessionSnapshot snapshot;
        private AuthFlowSessionMutationOutcome deleteOutcome = AuthFlowSessionMutationOutcome.APPLIED;
        private RevisionedAuthFlowSessionDeleteCommand lastDelete;
        private int loadCalls;
        private int deleteCalls;
        FakeAuthFlowPort(List<String> log) { this.log = log; }
        @Override public Mono<AuthFlowSession> create(AuthFlowSessionCreateCommand command) { throw new UnsupportedOperationException(); }
        @Override public Mono<Optional<AuthFlowSession>> load(AuthFlowSessionId id) { return Mono.just(Optional.ofNullable(snapshot).map(AuthFlowSessionSnapshot::session)); }
        @Override public Mono<Optional<AuthFlowSessionSnapshot>> loadSnapshot(AuthFlowSessionId id) { log.add("envelope.load"); loadCalls++; return Mono.just(Optional.ofNullable(snapshot)); }
        @Override public Mono<AuthFlowSessionDeleteResult> deleteRevisioned(RevisionedAuthFlowSessionDeleteCommand command) { log.add("envelope.delete"); deleteCalls++; lastDelete = command; return Mono.just(new AuthFlowSessionDeleteResult(deleteOutcome)); }
    }

    private static final class FakeWorkflowPort implements UsernameRecoveryWorkflowStatePort {
        private final List<String> log;
        private UsernameRecoveryWorkflowStateMutationOutcome deleteOutcome = UsernameRecoveryWorkflowStateMutationOutcome.DELETED;
        private int deleteCalls;
        FakeWorkflowPort(List<String> log) { this.log = log; }
        @Override public Mono<UsernameRecoveryWorkflowStateMutationResult> create(UsernameRecoveryWorkflowStateCreateCommand command) { throw new UnsupportedOperationException(); }
        @Override public Mono<UsernameRecoveryWorkflowStateLoadResult> load(AuthFlowSessionId id) { throw new UnsupportedOperationException(); }
        @Override public Mono<UsernameRecoveryWorkflowStateMutationResult> update(UsernameRecoveryWorkflowStateMutationCommand command) { throw new UnsupportedOperationException(); }
        @Override public Mono<UsernameRecoveryWorkflowStateDeleteResult> delete(AuthFlowSessionId id) { log.add("workflow.delete"); deleteCalls++; return Mono.just(new UsernameRecoveryWorkflowStateDeleteResult(deleteOutcome)); }
    }

    private static final class FakeIdempotencyPort implements UsernameRecoveryIdempotencyPort {
        private final List<String> log;
        private UsernameRecoveryIdempotencyOutcome reserveOutcome = UsernameRecoveryIdempotencyOutcome.RESERVED;
        private UsernameRecoveryIdempotencyRecord record;
        private int completeCalls;
        private int unknownCalls;
        FakeIdempotencyPort(List<String> log) { this.log = log; }
        @Override public Mono<ReservationResult> reserve(ReserveCommand command) {
            log.add("idem.reserve");
            if (reserveOutcome == UsernameRecoveryIdempotencyOutcome.RESERVED) {
                return Mono.just(new ReservationResult(reserveOutcome,
                        new UsernameRecoveryIdempotencyReservation(command.action(), command.idempotencyKey(),
                                command.fingerprint(), command.operationReference(), command.expiresAt())));
            }
            return Mono.just(new ReservationResult(reserveOutcome, null));
        }
        @Override public Mono<Optional<UsernameRecoveryIdempotencyRecord>> resolve(UsernameRecoveryIdempotencyAction action, UsernameRecoveryIdempotencyKey key) { log.add("idem.resolve"); return Mono.just(Optional.ofNullable(record)); }
        @Override public Mono<UsernameRecoveryIdempotencyRecord> complete(CompleteCommand command) { log.add("idem.complete"); completeCalls++; return Mono.just(new UsernameRecoveryIdempotencyRecord(command.action(), command.idempotencyKey(), command.fingerprint(), command.operationReference(), UsernameRecoveryIdempotencyRecord.Status.COMPLETED, command.replaySnapshot(), command.now(), command.now(), command.now().plus(command.ttl()))); }
        @Override public Mono<UsernameRecoveryIdempotencyRecord> recordUnknownOutcome(UnknownOutcomeCommand command) { log.add("idem.unknown"); unknownCalls++; return Mono.just(new UsernameRecoveryIdempotencyRecord(command.action(), command.idempotencyKey(), command.fingerprint(), command.operationReference(), UsernameRecoveryIdempotencyRecord.Status.UNKNOWN_OUTCOME, null, command.now(), command.now(), command.now().plus(command.ttl()))); }
    }
}
