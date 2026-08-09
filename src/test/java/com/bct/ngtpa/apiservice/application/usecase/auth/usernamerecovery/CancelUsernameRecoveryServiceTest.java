package com.bct.ngtpa.apiservice.application.usecase.auth.usernamerecovery;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.application.dto.auth.usernamerecovery.UsernameRecoveryCancelCommand;
import com.bct.ngtpa.apiservice.application.dto.auth.usernamerecovery.UsernameRecoveryCancelResult;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionId;
import com.bct.ngtpa.apiservice.application.port.out.UsernameRecoveryIdempotencyAction;
import com.bct.ngtpa.apiservice.application.port.out.UsernameRecoveryIdempotencyFingerprint;
import com.bct.ngtpa.apiservice.application.port.out.UsernameRecoveryIdempotencyKey;
import com.bct.ngtpa.apiservice.application.port.out.UsernameRecoveryIdempotencyOperationReference;
import com.bct.ngtpa.apiservice.application.port.out.UsernameRecoveryIdempotencyOutcome;
import com.bct.ngtpa.apiservice.application.port.out.UsernameRecoveryIdempotencyPort;
import com.bct.ngtpa.apiservice.application.port.out.UsernameRecoveryIdempotencyRecord;
import com.bct.ngtpa.apiservice.application.port.out.UsernameRecoveryIdempotencyReservation;
import com.bct.ngtpa.apiservice.application.port.out.UsernameRecoverySessionDeleteResult;
import com.bct.ngtpa.apiservice.application.port.out.UsernameRecoverySessionLoadResult;
import com.bct.ngtpa.apiservice.application.port.out.UsernameRecoverySessionMutationCommand;
import com.bct.ngtpa.apiservice.application.port.out.UsernameRecoverySessionMutationOutcome;
import com.bct.ngtpa.apiservice.application.port.out.UsernameRecoverySessionMutationResult;
import com.bct.ngtpa.apiservice.application.port.out.UsernameRecoverySessionPort;
import com.bct.ngtpa.apiservice.application.usecase.auth.usernamerecovery.UsernameRecoveryCancelException.Reason;
import com.bct.ngtpa.apiservice.application.usecase.session.authflow.CsrfTokenHasher;
import com.bct.ngtpa.apiservice.application.usecase.session.authflow.CsrfTokenValidationService;
import com.bct.ngtpa.apiservice.application.usecase.session.authflow.Sha256CsrfTokenHasher;
import com.bct.ngtpa.apiservice.domain.model.UsernameRecoveryCandidate;
import com.bct.ngtpa.apiservice.domain.model.UsernameRecoveryCsrfHash;
import com.bct.ngtpa.apiservice.domain.model.UsernameRecoveryDeliveryState;
import com.bct.ngtpa.apiservice.domain.model.UsernameRecoverySchemeRef;
import com.bct.ngtpa.apiservice.domain.model.UsernameRecoverySession;
import com.bct.ngtpa.apiservice.domain.model.UsernameRecoverySessionCandidate;
import com.bct.ngtpa.apiservice.domain.model.UsernameRecoverySessionId;
import com.bct.ngtpa.apiservice.domain.model.UsernameRecoverySessionRevision;
import com.bct.ngtpa.apiservice.domain.model.UsernameRecoveryVerifiedDestination;
import com.bct.ngtpa.apiservice.domain.model.UsernameRecoveryWorkflowStatus;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * Verifies the Task 021 {@link CancelUsernameRecoveryService} orchestration: fail-closed port
 * method, CANCEL fingerprinting, idempotency classification, origin/referer enforcement,
 * absent-session idempotent success, live-session CSRF gating, definitive deletion/cancellation,
 * stable unknown outcome on deletion uncertainty without automatic retry, completed replay with
 * no second deletion, constant redacted replay descriptor, and Java {@code Error} propagation.
 *
 * <p>Design references: {@code .cline/tasks/021-cancel-use-case.md} canonical behavior and
 * {@code docs/design/6-username-recovery/username-recovery-api-contract-design.md} section 8
 * (UR-CANCEL-001..003).
 */
class CancelUsernameRecoveryServiceTest {

    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final int TTL_SECONDS = 600;
    private static final String IDEM_KEY = "idem_12345678";
    private static final String CSRF = "csrf-token-abc";
    private static final String SESSION_ID = "afs_opaque-1";
    private static final String REGISTERED_REF = "sch_opaque-1";

    private final List<String> callLog = new ArrayList<>();
    private final FakeIdempotencyPort idempotencyPort = new FakeIdempotencyPort(callLog);
    private final FakeSessionPort sessionPort = new FakeSessionPort(callLog);

    private CancelUsernameRecoveryService service;

    @BeforeEach
    void setUp() {
        sessionPort.store(activeSession());
        service = newService(List.of());
    }

    private CancelUsernameRecoveryService newService(List<String> allowedOrigins) {
        return new CancelUsernameRecoveryService(
                new UsernameRecoveryCancelFingerprintGenerator(),
                idempotencyPort,
                sessionPort,
                new CsrfTokenValidationService(new Sha256CsrfTokenHasher()),
                allowedOrigins,
                CLOCK,
                TTL_SECONDS);
    }

    @Test
    void portMethodFailsClosedWithoutSecurityContext() {
        StepVerifier.create(service.cancel(command()))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(UsernameRecoveryCancelException.class);
                    assertThat(((UsernameRecoveryCancelException) error).reason())
                            .isEqualTo(Reason.SECURITY_CONTEXT_REQUIRED);
                })
                .verify();

        assertThat(callLog).isEmpty();
    }

    @Test
    void nullCommandMapsToTypedValidationFailureWithoutSideEffects() {
        StepVerifier.create(service.cancel(null, CSRF, null, null))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(UsernameRecoveryCancelException.class);
                    assertThat(((UsernameRecoveryCancelException) error).reason())
                            .isEqualTo(Reason.VALIDATION_FAILED);
                })
                .verify();

        assertThat(callLog).isEmpty();
    }

    @Test
    void unsafeIdempotencyKeyMapsToTypedValidationFailureWithoutSideEffects() {
        UsernameRecoveryCancelCommand command = new UsernameRecoveryCancelCommand(
                new AuthFlowSessionId(SESSION_ID), "not-a-valid key!");

        StepVerifier.create(service.cancel(command, CSRF, null, null))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(UsernameRecoveryCancelException.class);
                    assertThat(((UsernameRecoveryCancelException) error).reason())
                            .isEqualTo(Reason.VALIDATION_FAILED);
                })
                .verify();

        assertThat(callLog).isEmpty();
    }

    @Test
    void reserveInfrastructureErrorMapsToTypedSystemFailure() {
        idempotencyPort.reserveFails = true;

        StepVerifier.create(service.cancel(command(), CSRF, null, null))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(UsernameRecoveryCancelException.class);
                    assertThat(((UsernameRecoveryCancelException) error).reason())
                            .isEqualTo(Reason.SYSTEM_FAILURE);
                })
                .verify();

        assertThat(callLog).containsExactly("idem.reserve");
        assertThat(sessionPort.deleteCalls).isZero();
    }

    @Test
    void everyReserveClassificationIsDistinct() {
        assertReserveMapsToReason(UsernameRecoveryIdempotencyOutcome.DUPLICATE_IN_PROGRESS,
                Reason.IDEMPOTENCY_IN_PROGRESS);
        assertReserveMapsToReason(UsernameRecoveryIdempotencyOutcome.KEY_REUSED_WITH_DIFFERENT_REQUEST,
                Reason.IDEMPOTENCY_KEY_REUSED);
        assertReserveMapsToReason(UsernameRecoveryIdempotencyOutcome.UNKNOWN_OUTCOME_EXISTING,
                Reason.UNKNOWN_OUTCOME_EXISTING);
        assertReserveMapsToReason(UsernameRecoveryIdempotencyOutcome.SYSTEM_FAILURE,
                Reason.SYSTEM_FAILURE);
    }

    private void assertReserveMapsToReason(
            UsernameRecoveryIdempotencyOutcome outcome, Reason reason) {
        idempotencyPort.reserveOutcome = outcome;

        StepVerifier.create(service.cancel(command(), CSRF, null, null))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(UsernameRecoveryCancelException.class);
                    assertThat(((UsernameRecoveryCancelException) error).reason()).isEqualTo(reason);
                })
                .verify();

        assertThat(sessionPort.deleteCalls).isZero();
    }

    @Test
    void disallowedOriginFailsClosedAfterLiveSessionLoadBeforeDeletion() {
        service = newService(List.of("https://app.example"));

        StepVerifier.create(service.cancel(command(), CSRF, "https://evil.example", null))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(UsernameRecoveryCancelException.class);
                    assertThat(((UsernameRecoveryCancelException) error).reason())
                            .isEqualTo(Reason.SECURITY_INVALID);
                })
                .verify();

        assertThat(callLog).containsExactly("idem.reserve", "session.load");
        assertThat(sessionPort.loadCalls).isEqualTo(1);
        assertThat(sessionPort.deleteCalls).isZero();
    }

    @Test
    void allowedOriginPassesAndDeletionContinues() {
        service = newService(List.of("https://app.example"));

        StepVerifier.create(service.cancel(command(), CSRF, "https://app.example", null))
                .assertNext(result -> assertThat(result.outcome())
                        .isEqualTo(UsernameRecoveryCancelResult.Outcome.SUCCESS))
                .verifyComplete();

        assertThat(sessionPort.deleteCalls).isEqualTo(1);
    }
    @Test
    void absentSessionReturnsIdempotentSuccessWithoutDeleteOrSecurityContext() {
        sessionPort.stored = null;
        service = newService(List.of("https://app.example"));
        StepVerifier.create(service.cancel(command(), null, "https://evil.example", null))
                .assertNext(result -> assertThat(result.outcome())
                        .isEqualTo(UsernameRecoveryCancelResult.Outcome.SUCCESS))
                .verifyComplete();
        assertThat(callLog).containsExactly("idem.reserve", "session.load", "idem.complete");
        assertThat(sessionPort.deleteCalls).isZero();
        assertThat(idempotencyPort.completeCalls).isEqualTo(1);
        assertThat(idempotencyPort.lastCompleted.replaySnapshot())
                .isEqualTo(UsernameRecoveryCancelReplayCodec.encode());
    }
    @Test
    void presentLiveSessionDeletesExactlyOnceWithValidCsrf() {
        StepVerifier.create(service.cancel(command(), CSRF, null, null))
                .assertNext(result -> assertThat(result.outcome())
                        .isEqualTo(UsernameRecoveryCancelResult.Outcome.SUCCESS))
                .verifyComplete();

        assertThat(sessionPort.deleteCalls).isEqualTo(1);
        assertThat(idempotencyPort.completeCalls).isEqualTo(1);
        assertThat(idempotencyPort.unknownOutcomeCalls).isZero();
        assertThat(callLog).contains("idem.reserve", "session.load", "session.delete", "idem.complete");
    }

    @Test
    void alreadyAbsentDeletionStillCompletesSuccess() {
        sessionPort.deleteOutcome = UsernameRecoverySessionMutationOutcome.ALREADY_ABSENT;

        StepVerifier.create(service.cancel(command(), CSRF, null, null))
                .assertNext(result -> assertThat(result.outcome())
                        .isEqualTo(UsernameRecoveryCancelResult.Outcome.SUCCESS))
                .verifyComplete();

        assertThat(sessionPort.deleteCalls).isEqualTo(1);
        assertThat(idempotencyPort.completeCalls).isEqualTo(1);
    }

    @Test
    void invalidCsrfPreservesLiveSession() {
        StepVerifier.create(service.cancel(command(), "wrong-csrf", null, null))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(UsernameRecoveryCancelException.class);
                    assertThat(((UsernameRecoveryCancelException) error).reason())
                            .isEqualTo(Reason.SECURITY_INVALID);
                })
                .verify();

        assertThat(sessionPort.deleteCalls).isZero();
        assertThat(idempotencyPort.completeCalls).isZero();
        assertThat(callLog).doesNotContain("session.delete");
    }

    @Test
    void missingCsrfPreservesLiveSession() {
        StepVerifier.create(service.cancel(command(), null, null, null))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(UsernameRecoveryCancelException.class);
                    assertThat(((UsernameRecoveryCancelException) error).reason())
                            .isEqualTo(Reason.SECURITY_INVALID);
                })
                .verify();

        assertThat(sessionPort.deleteCalls).isZero();
    }

    @Test
    void expiredSessionCancelsSuccessfullyWithoutCsrfGate() {
        UsernameRecoverySession stored = activeSession();
        sessionPort.store(new UsernameRecoverySession(
                stored.sessionId(), stored.scenario(), stored.status(), stored.revision(),
                stored.csrfHash(), stored.destination(), stored.language(),
                NOW.minusSeconds(10), NOW.minusSeconds(1), stored.candidates()));

        StepVerifier.create(service.cancel(command(), null, null, null))
                .assertNext(result -> assertThat(result.outcome())
                        .isEqualTo(UsernameRecoveryCancelResult.Outcome.SUCCESS))
                .verifyComplete();

        assertThat(sessionPort.deleteCalls).isEqualTo(1);
        assertThat(idempotencyPort.completeCalls).isEqualTo(1);
    }

    @Test
    void terminalSessionCancelsSuccessfullyWithoutCsrfGate() {
        sessionPort.store(completedSession());

        StepVerifier.create(service.cancel(command(), null, null, null))
                .assertNext(result -> assertThat(result.outcome())
                        .isEqualTo(UsernameRecoveryCancelResult.Outcome.SUCCESS))
                .verifyComplete();

        assertThat(sessionPort.deleteCalls).isEqualTo(1);
        assertThat(idempotencyPort.completeCalls).isEqualTo(1);
    }

    @Test
    void cancelledSessionCancelsSuccessfullyWithoutCsrfGate() {
        sessionPort.store(sessionWithStatus(UsernameRecoveryWorkflowStatus.CANCELLED));

        StepVerifier.create(service.cancel(command(), null, null, null))
                .assertNext(result -> assertThat(result.outcome())
                        .isEqualTo(UsernameRecoveryCancelResult.Outcome.SUCCESS))
                .verifyComplete();

        assertThat(sessionPort.deleteCalls).isEqualTo(1);
        assertThat(idempotencyPort.completeCalls).isEqualTo(1);
    }
    @Test
    void loadInfrastructureFailureRecordsStableUnknownOutcomeWithoutDeletion() {
        sessionPort.loadFails = true;
        StepVerifier.create(service.cancel(command(), CSRF, null, null))
                .assertNext(result -> assertThat(result.outcome())
                        .isEqualTo(UsernameRecoveryCancelResult.Outcome.SYSTEM_FAILED))
                .verifyComplete();
        assertThat(sessionPort.deleteCalls).isZero();
        assertThat(idempotencyPort.completeCalls).isZero();
        assertThat(idempotencyPort.unknownOutcomeCalls).isEqualTo(1);
    }
    @Test
    void deleteUncertainOutcomeRecordsStableUnknownOutcomeAndNeverSuccess() {
        sessionPort.deleteOutcome = UsernameRecoverySessionMutationOutcome.SYSTEM_FAILURE;

        StepVerifier.create(service.cancel(command(), CSRF, null, null))
                .assertNext(result -> assertThat(result.outcome())
                        .isEqualTo(UsernameRecoveryCancelResult.Outcome.SYSTEM_FAILED))
                .verifyComplete();

        assertThat(sessionPort.deleteCalls).isEqualTo(1);
        assertThat(idempotencyPort.completeCalls).isZero();
        assertThat(idempotencyPort.unknownOutcomeCalls).isEqualTo(1);
        assertThat(idempotencyPort.lastUnknownOutcome.action())
                .isEqualTo(UsernameRecoveryIdempotencyAction.CANCEL);
    }

    @Test
    void deleteInfrastructureExceptionRecordsStableUnknownOutcome() {
        sessionPort.deleteFails = true;

        StepVerifier.create(service.cancel(command(), CSRF, null, null))
                .assertNext(result -> assertThat(result.outcome())
                        .isEqualTo(UsernameRecoveryCancelResult.Outcome.SYSTEM_FAILED))
                .verifyComplete();

        assertThat(sessionPort.deleteCalls).isEqualTo(1);
        assertThat(idempotencyPort.completeCalls).isZero();
        assertThat(idempotencyPort.unknownOutcomeCalls).isEqualTo(1);
    }

    @Test
    void completeFailureAfterDefinitiveDeleteRecordsStableUnknownOutcome() {
        idempotencyPort.completeFails = true;

        StepVerifier.create(service.cancel(command(), CSRF, null, null))
                .assertNext(result -> assertThat(result.outcome())
                        .isEqualTo(UsernameRecoveryCancelResult.Outcome.SYSTEM_FAILED))
                .verifyComplete();

        assertThat(sessionPort.deleteCalls).isEqualTo(1);
        assertThat(idempotencyPort.completeCalls).isEqualTo(1);
        assertThat(idempotencyPort.unknownOutcomeCalls).isEqualTo(1);
    }

    @Test
    void unknownOutcomeRecordingFailureMapsToDefiniteSystemFailure() {
        sessionPort.deleteOutcome = UsernameRecoverySessionMutationOutcome.SYSTEM_FAILURE;
        idempotencyPort.unknownOutcomeFails = true;

        StepVerifier.create(service.cancel(command(), CSRF, null, null))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(UsernameRecoveryCancelException.class);
                    assertThat(((UsernameRecoveryCancelException) error).reason())
                            .isEqualTo(Reason.SYSTEM_FAILURE);
                })
                .verify();

        assertThat(idempotencyPort.completeCalls).isZero();
        assertThat(idempotencyPort.unknownOutcomeCalls).isEqualTo(1);
    }

    @Test
    void csrfValidationInfrastructureFailureMapsToTypedSystemFailure() {
        service = new CancelUsernameRecoveryService(
                new UsernameRecoveryCancelFingerprintGenerator(),
                idempotencyPort,
                sessionPort,
                new CsrfTokenValidationService(new ThrowingCsrfTokenHasher()),
                List.of(),
                CLOCK,
                TTL_SECONDS);

        StepVerifier.create(service.cancel(command(), CSRF, null, null))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(UsernameRecoveryCancelException.class);
                    assertThat(((UsernameRecoveryCancelException) error).reason())
                            .isEqualTo(Reason.SYSTEM_FAILURE);
                })
                .verify();

        assertThat(sessionPort.deleteCalls).isZero();
    }
    @Test
    void javaErrorFromCsrfValidationPropagatesUntouched() {
        service = new CancelUsernameRecoveryService(
                new UsernameRecoveryCancelFingerprintGenerator(), idempotencyPort, sessionPort,
                new CsrfTokenValidationService(new ErrorCsrfTokenHasher()),
                List.of(), CLOCK, TTL_SECONDS);
        StepVerifier.create(service.cancel(command(), CSRF, null, null))
                .expectError(AssertionError.class).verify();
        assertThat(sessionPort.deleteCalls).isZero();
    }



    @Test
    void completedReplayReturnsSuccessWithoutSecondDelete() {
        sessionPort.store(sessionWithStatus(UsernameRecoveryWorkflowStatus.PARTIALLY_REQUESTED));
        idempotencyPort.reserveOutcome = UsernameRecoveryIdempotencyOutcome.COMPLETED_REPLAY;
        idempotencyPort.storedRecord = completedRecord(UsernameRecoveryCancelReplayCodec.encode());

        StepVerifier.create(service.cancel(command(), CSRF, null, null))
                .assertNext(result -> assertThat(result.outcome())
                        .isEqualTo(UsernameRecoveryCancelResult.Outcome.SUCCESS))
                .verifyComplete();

        assertThat(callLog).containsExactly("idem.reserve", "idem.resolve");
        assertThat(sessionPort.loadCalls).isZero();
        assertThat(sessionPort.deleteCalls).isZero();
        assertThat(idempotencyPort.completeCalls).isZero();
        assertThat(idempotencyPort.resolveCalls).isEqualTo(1);
    }
    @Test
    void completedReplayWithWrongActionFailsClosed() {
        idempotencyPort.reserveOutcome = UsernameRecoveryIdempotencyOutcome.COMPLETED_REPLAY;
        idempotencyPort.storedRecord = completedRecordFor(
                UsernameRecoveryIdempotencyAction.REQUEST,
                new UsernameRecoveryIdempotencyKey(IDEM_KEY));
        StepVerifier.create(service.cancel(command(), CSRF, null, null))
                .expectError(UsernameRecoveryCancelException.class).verify();
        assertThat(sessionPort.deleteCalls).isZero();
    }

    @Test
    void completedReplayWithWrongKeyFailsClosed() {
        idempotencyPort.reserveOutcome = UsernameRecoveryIdempotencyOutcome.COMPLETED_REPLAY;
        idempotencyPort.storedRecord = completedRecordFor(
                UsernameRecoveryIdempotencyAction.CANCEL,
                new UsernameRecoveryIdempotencyKey("other_key_1234"));
        StepVerifier.create(service.cancel(command(), CSRF, null, null))
                .expectError(UsernameRecoveryCancelException.class).verify();
        assertThat(sessionPort.deleteCalls).isZero();
    }


    @Test
    void completedReplayWithMalformedSnapshotFailsClosed() {
        idempotencyPort.reserveOutcome = UsernameRecoveryIdempotencyOutcome.COMPLETED_REPLAY;
        idempotencyPort.storedRecord = completedRecord(
                new UsernameRecoveryIdempotencyRecord.ReplaySnapshot("unrelated-descriptor"));

        StepVerifier.create(service.cancel(command(), CSRF, null, null))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(UsernameRecoveryCancelException.class);
                    assertThat(((UsernameRecoveryCancelException) error).reason())
                            .isEqualTo(Reason.SYSTEM_FAILURE);
                })
                .verify();

        assertThat(sessionPort.deleteCalls).isZero();
    }

    @Test
    void completedReplayWithMissingRecordFailsClosed() {
        idempotencyPort.reserveOutcome = UsernameRecoveryIdempotencyOutcome.COMPLETED_REPLAY;
        idempotencyPort.storedRecord = null;

        StepVerifier.create(service.cancel(command(), CSRF, null, null))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(UsernameRecoveryCancelException.class);
                    assertThat(((UsernameRecoveryCancelException) error).reason())
                            .isEqualTo(Reason.SYSTEM_FAILURE);
                })
                .verify();

        assertThat(sessionPort.deleteCalls).isZero();
    }

    @Test
    void completedReplayWithContradictoryRecordStateFailsClosed() {
        idempotencyPort.reserveOutcome = UsernameRecoveryIdempotencyOutcome.COMPLETED_REPLAY;
        idempotencyPort.storedRecord = completedRecord(
                new UsernameRecoveryIdempotencyRecord.ReplaySnapshot("ur-cancel-completed:v1:extra"));

        StepVerifier.create(service.cancel(command(), CSRF, null, null))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(UsernameRecoveryCancelException.class);
                    assertThat(((UsernameRecoveryCancelException) error).reason())
                            .isEqualTo(Reason.SYSTEM_FAILURE);
                })
                .verify();

        assertThat(sessionPort.deleteCalls).isZero();
    }

    @Test
    void completedReplayWithUnknownOutcomeRecordFailsClosed() {
        idempotencyPort.reserveOutcome = UsernameRecoveryIdempotencyOutcome.COMPLETED_REPLAY;
        idempotencyPort.storedRecord = unknownOutcomeRecord();

        StepVerifier.create(service.cancel(command(), CSRF, null, null))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(UsernameRecoveryCancelException.class);
                    assertThat(((UsernameRecoveryCancelException) error).reason())
                            .isEqualTo(Reason.SYSTEM_FAILURE);
                })
                .verify();

        assertThat(sessionPort.deleteCalls).isZero();
    }

    @Test
    void resolveInfrastructureFailureOnReplayFailsClosed() {
        idempotencyPort.reserveOutcome = UsernameRecoveryIdempotencyOutcome.COMPLETED_REPLAY;
        idempotencyPort.resolveFails = true;

        StepVerifier.create(service.cancel(command(), CSRF, null, null))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(UsernameRecoveryCancelException.class);
                    assertThat(((UsernameRecoveryCancelException) error).reason())
                            .isEqualTo(Reason.SYSTEM_FAILURE);
                })
                .verify();

        assertThat(sessionPort.deleteCalls).isZero();
    }

    @Test
    void javaErrorFromSessionLoadPropagatesUntouched() {
        sessionPort.loadError = new AssertionError("programming defect");

        StepVerifier.create(service.cancel(command(), CSRF, null, null))
                .expectError(AssertionError.class)
                .verify();

        assertThat(sessionPort.deleteCalls).isZero();
    }

    @Test
    void javaErrorFromSessionDeletePropagatesUntouched() {
        sessionPort.deleteError = new AssertionError("programming defect");

        StepVerifier.create(service.cancel(command(), CSRF, null, null))
                .expectError(AssertionError.class)
                .verify();

        assertThat(idempotencyPort.unknownOutcomeCalls).isZero();
    }

    @Test
    void cancelNeverClearsCookiesOrWritesWebState() {
        StepVerifier.create(service.cancel(command(), CSRF, null, null))
                .expectNextCount(1)
                .verifyComplete();

        assertThat(callLog).doesNotContain("cookie", "web", "http");
    }


    private static UsernameRecoveryCancelCommand command() {
        return new UsernameRecoveryCancelCommand(new AuthFlowSessionId(SESSION_ID), IDEM_KEY);
    }

    private static UsernameRecoverySession activeSession() {
        UsernameRecoverySessionId sessionId = new UsernameRecoverySessionId(SESSION_ID);
        UsernameRecoverySchemeRef registeredRef = new UsernameRecoverySchemeRef(REGISTERED_REF);
        List<UsernameRecoverySessionCandidate> candidates = List.of(
                new UsernameRecoverySessionCandidate(
                        new UsernameRecoveryCandidate(registeredRef, "DB", "DBA", true, "user-1",
                                UsernameRecoveryDeliveryState.AVAILABLE), null, null));
        return new UsernameRecoverySession(
                sessionId,
                UsernameRecoverySession.Scenario.USERNAME_RECOVERY,
                UsernameRecoveryWorkflowStatus.ACTIVE,
                new UsernameRecoverySessionRevision(0),
                new UsernameRecoveryCsrfHash(new Sha256CsrfTokenHasher().hash(CSRF)),
                new UsernameRecoveryVerifiedDestination("pepperc@ah.ozk",
                        UsernameRecoveryVerifiedDestination.Channel.EMAIL),
                Locale.ENGLISH,
                NOW,
                NOW.plusSeconds(TTL_SECONDS),
                candidates);
    }

    private static UsernameRecoverySession sessionWithStatus(UsernameRecoveryWorkflowStatus status) {
        UsernameRecoverySession active = activeSession();
        return new UsernameRecoverySession(
                active.sessionId(), active.scenario(), status, active.revision(),
                active.csrfHash(), active.destination(), active.language(),
                active.createdAt(), active.expiresAt(), active.candidates());
    }

    private static UsernameRecoverySession completedSession() {
        UsernameRecoverySession active = activeSession();
        UsernameRecoverySchemeRef registeredRef = new UsernameRecoverySchemeRef(REGISTERED_REF);
        UsernameRecoverySessionCandidate requested = new UsernameRecoverySessionCandidate(
                new UsernameRecoveryCandidate(registeredRef, "DB", "DBA", true, "user-1",
                        UsernameRecoveryDeliveryState.REQUESTED), NOW, "op_sender_1");
        return new UsernameRecoverySession(
                active.sessionId(), active.scenario(), UsernameRecoveryWorkflowStatus.COMPLETED,
                active.revision(), active.csrfHash(), active.destination(), active.language(),
                active.createdAt(), active.expiresAt(), List.of(requested));
    }
    private static UsernameRecoveryIdempotencyRecord completedRecordFor(
            UsernameRecoveryIdempotencyAction action,
            UsernameRecoveryIdempotencyKey key) {
        return new UsernameRecoveryIdempotencyRecord(
                action,
                key,
                new UsernameRecoveryIdempotencyFingerprint(
                        "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"),
                new UsernameRecoveryIdempotencyOperationReference("op-ref"),
                UsernameRecoveryIdempotencyRecord.Status.COMPLETED,
                UsernameRecoveryCancelReplayCodec.encode(),
                NOW,
                NOW,
                NOW.plusSeconds(TTL_SECONDS));
    }


    private static UsernameRecoveryIdempotencyRecord completedRecord(
            UsernameRecoveryIdempotencyRecord.ReplaySnapshot snapshot) {
        return new UsernameRecoveryIdempotencyRecord(
                UsernameRecoveryIdempotencyAction.CANCEL,
                new UsernameRecoveryIdempotencyKey(IDEM_KEY),
                new UsernameRecoveryIdempotencyFingerprint(
                        "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"),
                new UsernameRecoveryIdempotencyOperationReference("op-ref"),
                UsernameRecoveryIdempotencyRecord.Status.COMPLETED,
                snapshot,
                NOW,
                NOW,
                NOW.plusSeconds(TTL_SECONDS));
    }

    private static UsernameRecoveryIdempotencyRecord unknownOutcomeRecord() {
        return new UsernameRecoveryIdempotencyRecord(
                UsernameRecoveryIdempotencyAction.CANCEL,
                new UsernameRecoveryIdempotencyKey(IDEM_KEY),
                new UsernameRecoveryIdempotencyFingerprint(
                        "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"),
                new UsernameRecoveryIdempotencyOperationReference("op-ref"),
                UsernameRecoveryIdempotencyRecord.Status.UNKNOWN_OUTCOME,
                null,
                NOW,
                NOW,
                NOW.plusSeconds(TTL_SECONDS));
    }


    private static final class ThrowingCsrfTokenHasher implements CsrfTokenHasher {
        @Override
        public String hash(String rawToken) {
            throw new IllegalStateException("hasher defect");
        }

        @Override
        public boolean matches(String rawToken, String tokenHash) {
            throw new IllegalStateException("hasher defect");
        }
    }
    private static final class ErrorCsrfTokenHasher implements CsrfTokenHasher {
        @Override public String hash(String rawToken) { throw new AssertionError("programming defect"); }
        @Override public boolean matches(String rawToken, String tokenHash) { throw new AssertionError("programming defect"); }
    }


    private static final class FakeIdempotencyPort implements UsernameRecoveryIdempotencyPort {

        private final List<String> callLog;
        private UsernameRecoveryIdempotencyOutcome reserveOutcome =
                UsernameRecoveryIdempotencyOutcome.RESERVED;
        private boolean reserveFails;
        private boolean completeFails;
        private boolean resolveFails;
        private boolean unknownOutcomeFails;
        private UsernameRecoveryIdempotencyRecord storedRecord;
        private UsernameRecoveryIdempotencyPort.CompleteCommand lastCompleted;
        private UsernameRecoveryIdempotencyPort.UnknownOutcomeCommand lastUnknownOutcome;
        private int completeCalls;
        private int unknownOutcomeCalls;
        private int resolveCalls;

        private FakeIdempotencyPort(List<String> callLog) {
            this.callLog = callLog;
        }

        @Override
        public Mono<ReservationResult> reserve(ReserveCommand command) {
            callLog.add("idem.reserve");
            if (reserveFails) {
                return Mono.error(new IllegalStateException("reserve infrastructure failure"));
            }
            if (reserveOutcome == UsernameRecoveryIdempotencyOutcome.RESERVED) {
                UsernameRecoveryIdempotencyReservation reservation =
                        new UsernameRecoveryIdempotencyReservation(
                                command.action(),
                                command.idempotencyKey(),
                                command.fingerprint(),
                                command.operationReference(),
                                command.now().plus(command.ttl()));
                return Mono.just(new ReservationResult(reserveOutcome, reservation));
            }
            return Mono.just(new ReservationResult(reserveOutcome, null));
        }

        @Override
        public Mono<Optional<UsernameRecoveryIdempotencyRecord>> resolve(
                UsernameRecoveryIdempotencyAction action,
                UsernameRecoveryIdempotencyKey idempotencyKey) {
            callLog.add("idem.resolve");
            resolveCalls++;
            if (resolveFails) {
                return Mono.error(new IllegalStateException("resolve infrastructure failure"));
            }
            return Mono.just(Optional.ofNullable(storedRecord));
        }

        @Override
        public Mono<UsernameRecoveryIdempotencyRecord> complete(CompleteCommand command) {
            callLog.add("idem.complete");
            completeCalls++;
            lastCompleted = command;
            if (completeFails) {
                return Mono.error(new IllegalStateException("complete infrastructure failure"));
            }
            return Mono.just(new UsernameRecoveryIdempotencyRecord(
                    command.action(),
                    command.idempotencyKey(),
                    command.fingerprint(),
                    command.operationReference(),
                    UsernameRecoveryIdempotencyRecord.Status.COMPLETED,
                    command.replaySnapshot(),
                    command.now(),
                    command.now(),
                    command.now().plus(command.ttl())));
        }

        @Override
        public Mono<UsernameRecoveryIdempotencyRecord> recordUnknownOutcome(
                UnknownOutcomeCommand command) {
            callLog.add("idem.unknownOutcome");
            unknownOutcomeCalls++;
            lastUnknownOutcome = command;
            if (unknownOutcomeFails) {
                return Mono.error(new IllegalStateException(
                        "unknown outcome infrastructure failure"));
            }
            return Mono.just(new UsernameRecoveryIdempotencyRecord(
                    command.action(),
                    command.idempotencyKey(),
                    command.fingerprint(),
                    command.operationReference(),
                    UsernameRecoveryIdempotencyRecord.Status.UNKNOWN_OUTCOME,
                    null,
                    command.now(),
                    command.now(),
                    command.now().plus(command.ttl())));
        }
    }


    private static final class FakeSessionPort implements UsernameRecoverySessionPort {

        private final List<String> callLog;
        private UsernameRecoverySession stored;
        private boolean loadFails;
        private boolean deleteFails;
        private Throwable loadError;
        private Throwable deleteError;
        private UsernameRecoverySessionMutationOutcome deleteOutcome =
                UsernameRecoverySessionMutationOutcome.DELETED;
        private int deleteCalls;
        private int loadCalls;

        private FakeSessionPort(List<String> callLog) {
            this.callLog = callLog;
        }

        private void store(UsernameRecoverySession session) {
            this.stored = session;
        }

        @Override
        public Mono<UsernameRecoverySessionMutationResult> create(UsernameRecoverySession session) {
            throw new UnsupportedOperationException("cancel flow never creates sessions");
        }

        @Override
        public Mono<UsernameRecoverySessionLoadResult> load(UsernameRecoverySessionId sessionId) {
            callLog.add("session.load");
            loadCalls++;
            if (loadError != null) {
                return Mono.error(loadError);
            }
            if (loadFails) {
                return Mono.error(new IllegalStateException("session load infrastructure failure"));
            }
            if (stored == null) {
                return Mono.just(UsernameRecoverySessionLoadResult.notFound());
            }
            return Mono.just(UsernameRecoverySessionLoadResult.loaded(stored));
        }

        @Override
        public Mono<UsernameRecoverySessionMutationResult> update(
                UsernameRecoverySessionMutationCommand command) {
            throw new UnsupportedOperationException("cancel flow never updates sessions");
        }

        @Override
        public Mono<UsernameRecoverySessionDeleteResult> delete(UsernameRecoverySessionId sessionId) {
            callLog.add("session.delete");
            deleteCalls++;
            if (deleteError != null) {
                return Mono.error(deleteError);
            }
            if (deleteFails) {
                return Mono.error(new IllegalStateException("session delete infrastructure failure"));
            }
            return Mono.just(new UsernameRecoverySessionDeleteResult(deleteOutcome));
        }
    }
}

