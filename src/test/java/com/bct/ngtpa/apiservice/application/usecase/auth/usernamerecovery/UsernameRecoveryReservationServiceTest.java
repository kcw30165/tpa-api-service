package com.bct.ngtpa.apiservice.application.usecase.auth.usernamerecovery;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowScenario;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSession;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionData;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionId;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionSnapshot;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowStatus;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.SessionRevision;
import com.bct.ngtpa.apiservice.application.dto.session.usernamerecovery.UsernameRecoveryWorkflowState;
import com.bct.ngtpa.apiservice.application.dto.session.usernamerecovery.UsernameRecoveryWorkflowStateRevision;
import com.bct.ngtpa.apiservice.application.port.out.AuthFlowSessionPort;
import com.bct.ngtpa.apiservice.application.port.out.UsernameRecoveryWorkflowStateDeleteResult;
import com.bct.ngtpa.apiservice.application.port.out.UsernameRecoveryWorkflowStateLoadResult;
import com.bct.ngtpa.apiservice.application.port.out.UsernameRecoveryWorkflowStateMutationCommand;
import com.bct.ngtpa.apiservice.application.port.out.UsernameRecoveryWorkflowStateMutationOutcome;
import com.bct.ngtpa.apiservice.application.port.out.UsernameRecoveryWorkflowStateMutationResult;
import com.bct.ngtpa.apiservice.application.port.out.UsernameRecoveryWorkflowStatePort;
import com.bct.ngtpa.apiservice.application.usecase.session.authflow.CsrfTokenValidationService;
import com.bct.ngtpa.apiservice.application.usecase.session.authflow.Sha256CsrfTokenHasher;
import com.bct.ngtpa.apiservice.domain.model.UsernameRecoveryCandidate;
import com.bct.ngtpa.apiservice.domain.model.UsernameRecoveryDeliveryState;
import com.bct.ngtpa.apiservice.domain.model.UsernameRecoverySchemeRef;
import com.bct.ngtpa.apiservice.domain.model.UsernameRecoverySessionCandidate;
import com.bct.ngtpa.apiservice.domain.model.UsernameRecoveryVerifiedDestination;
import com.bct.ngtpa.apiservice.domain.model.UsernameRecoveryWorkflowStatus;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class UsernameRecoveryReservationServiceTest {
    private static final Instant NOW = Instant.parse("2026-01-01T00:05:00Z");
    private static final AuthFlowSessionId ID = new AuthFlowSessionId("afs_opaque-1");
    private static final UsernameRecoverySchemeRef SCHEME = new UsernameRecoverySchemeRef("sch_1");
    private static final String TOKEN = "raw-csrf-token-abc";
    private final Sha256CsrfTokenHasher hasher = new Sha256CsrfTokenHasher();
    private final FakeAuthFlowPort envelopes = new FakeAuthFlowPort();
    private final FakeWorkflowPort workflows = new FakeWorkflowPort();
    private UsernameRecoveryReservationService service;

    @BeforeEach
    void setUp() {
        envelopes.snapshot = snapshot(AuthFlowScenario.USERNAME_RECOVERY, AuthFlowStatus.STARTED,
                NOW.plusSeconds(300), hasher.hash(TOKEN));
        workflows.state = state(UsernameRecoveryWorkflowStatus.ACTIVE,
                UsernameRecoveryDeliveryState.AVAILABLE, ID);
        service = new UsernameRecoveryReservationService(
                envelopes, workflows, new CsrfTokenValidationService(hasher));
    }

    @Test
    void reservesThroughEnvelopeAndSidecarCas() {
        assertOutcome(command(SCHEME), TOKEN, UsernameRecoveryReservationOutcome.RESERVED);
        assertThat(envelopes.loads).isEqualTo(1);
        assertThat(workflows.loads).isEqualTo(1);
        assertThat(workflows.updates).isEqualTo(1);
        assertThat(workflows.lastMutation.expectedRevision().value()).isZero();
        assertThat(workflows.lastMutation.updatedState().revision().value()).isEqualTo(1);
        assertThat(workflows.state.candidates().get(0).deliveryState())
                .isEqualTo(UsernameRecoveryDeliveryState.RESERVED);
    }

    @Test
    void missingEnvelopeAndSidecarMapToAbsent() {
        envelopes.snapshot = null;
        assertOutcome(command(SCHEME), TOKEN, UsernameRecoveryReservationOutcome.SESSION_ABSENT);
        assertThat(workflows.loads).isZero();
        envelopes.snapshot = snapshot(AuthFlowScenario.USERNAME_RECOVERY, AuthFlowStatus.STARTED,
                NOW.plusSeconds(300), hasher.hash(TOKEN));
        workflows.state = null;
        assertOutcome(command(SCHEME), TOKEN, UsernameRecoveryReservationOutcome.SESSION_ABSENT);
    }

    @Test
    void expiredEnvelopeMapsToExpiredWithoutSidecarAccess() {
        envelopes.snapshot = snapshot(AuthFlowScenario.USERNAME_RECOVERY, AuthFlowStatus.STARTED,
                NOW, hasher.hash(TOKEN));
        assertOutcome(command(SCHEME), TOKEN, UsernameRecoveryReservationOutcome.SESSION_EXPIRED);
        assertThat(workflows.loads).isZero();
    }

    @Test
    void wrongScenarioAndInvalidLifecycleFailClosed() {
        envelopes.snapshot = snapshot(AuthFlowScenario.LOGIN, AuthFlowStatus.STARTED,
                NOW.plusSeconds(300), hasher.hash(TOKEN));
        assertOutcome(command(SCHEME), TOKEN, UsernameRecoveryReservationOutcome.SESSION_ABSENT);
        envelopes.snapshot = snapshot(AuthFlowScenario.USERNAME_RECOVERY, AuthFlowStatus.COMPLETED,
                NOW.plusSeconds(300), hasher.hash(TOKEN));
        assertOutcome(command(SCHEME), TOKEN, UsernameRecoveryReservationOutcome.WORKFLOW_TERMINAL);
        envelopes.snapshot = snapshot(AuthFlowScenario.USERNAME_RECOVERY, AuthFlowStatus.OTP_SENT,
                NOW.plusSeconds(300), hasher.hash(TOKEN));
        assertOutcome(command(SCHEME), TOKEN, UsernameRecoveryReservationOutcome.WORKFLOW_TERMINAL);
        assertThat(workflows.loads).isZero();
    }

    @Test
    void csrfFailureOccursBeforeSidecarAccess() {
        assertOutcome(command(SCHEME), "wrong", UsernameRecoveryReservationOutcome.CSRF_INVALID);
        assertThat(workflows.loads).isZero();
    }

    @Test
    void identityMismatchAndTerminalSidecarFailClosed() {
        workflows.state = state(UsernameRecoveryWorkflowStatus.ACTIVE,
                UsernameRecoveryDeliveryState.AVAILABLE, new AuthFlowSessionId("afs_other"));
        assertOutcome(command(SCHEME), TOKEN, UsernameRecoveryReservationOutcome.SYSTEM_FAILED);
        workflows.state = state(UsernameRecoveryWorkflowStatus.COMPLETED,
                UsernameRecoveryDeliveryState.REQUESTED, ID);
        assertOutcome(command(SCHEME), TOKEN, UsernameRecoveryReservationOutcome.WORKFLOW_TERMINAL);
        assertThat(workflows.updates).isZero();
    }

    @Test
    void candidateEligibilityMappingsRemainTyped() {
        assertOutcome(command(new UsernameRecoverySchemeRef("sch_missing")), TOKEN,
                UsernameRecoveryReservationOutcome.SCHEME_REF_INVALID);
        workflows.state = stateWithRegistration(false, UsernameRecoveryDeliveryState.AVAILABLE);
        assertOutcome(command(SCHEME), TOKEN, UsernameRecoveryReservationOutcome.REGISTRATION_REQUIRED);
        workflows.state = partiallyRequestedState();
        assertOutcome(command(SCHEME), TOKEN, UsernameRecoveryReservationOutcome.ALREADY_REQUESTED);
        workflows.state = state(UsernameRecoveryWorkflowStatus.ACTIVE,
                UsernameRecoveryDeliveryState.RESERVED, ID);
        assertOutcome(command(SCHEME), TOKEN, UsernameRecoveryReservationOutcome.CANDIDATE_NOT_AVAILABLE);
    }

    @Test
    void sidecarMutationOutcomesRemainDistinctAndAreNotRetried() {
        workflows.updateOutcome = UsernameRecoveryWorkflowStateMutationOutcome.REVISION_CONFLICT;
        assertOutcome(command(SCHEME), TOKEN, UsernameRecoveryReservationOutcome.REVISION_CONFLICT);
        assertThat(workflows.updates).isEqualTo(1);
        workflows.updates = 0;
        workflows.updateOutcome = UsernameRecoveryWorkflowStateMutationOutcome.STATE_NOT_FOUND;
        assertOutcome(command(SCHEME), TOKEN, UsernameRecoveryReservationOutcome.SESSION_ABSENT);
        assertThat(workflows.updates).isEqualTo(1);
    }

    @Test
    void infrastructureFailuresMapToSystemFailure() {
        envelopes.fail = true;
        assertOutcome(command(SCHEME), TOKEN, UsernameRecoveryReservationOutcome.SYSTEM_FAILED);
        envelopes.fail = false;
        workflows.loadFail = true;
        assertOutcome(command(SCHEME), TOKEN, UsernameRecoveryReservationOutcome.SYSTEM_FAILED);
        workflows.loadFail = false;
        workflows.updateFail = true;
        assertOutcome(command(SCHEME), TOKEN, UsernameRecoveryReservationOutcome.SYSTEM_FAILED);
    }

    private void assertOutcome(UsernameRecoveryReservationCommand command, String token,
            UsernameRecoveryReservationOutcome expected) {
        StepVerifier.create(service.reserve(command, token))
                .assertNext(result -> assertThat(result.outcome()).isEqualTo(expected))
                .verifyComplete();
    }

    private static UsernameRecoveryReservationCommand command(UsernameRecoverySchemeRef scheme) {
        return new UsernameRecoveryReservationCommand(ID, scheme, NOW);
    }

    private AuthFlowSessionSnapshot snapshot(AuthFlowScenario scenario, AuthFlowStatus status,
            Instant expiry, String hash) {
        AuthFlowSession session = new AuthFlowSession(ID, scenario, status, hash, null,
                AuthFlowSessionData.empty(), NOW.minusSeconds(60), NOW.minusSeconds(60), expiry);
        return new AuthFlowSessionSnapshot(session,
                new SessionRevision("0123456789abcdef0123456789abcdef"));
    }

    private static UsernameRecoveryWorkflowState state(UsernameRecoveryWorkflowStatus status,
            UsernameRecoveryDeliveryState delivery, AuthFlowSessionId id) {
        return new UsernameRecoveryWorkflowState(id, status,
                new UsernameRecoveryWorkflowStateRevision(0),
                new UsernameRecoveryVerifiedDestination("p****c@example.com",
                        UsernameRecoveryVerifiedDestination.Channel.EMAIL),
                Locale.ENGLISH,
                List.of(new UsernameRecoverySessionCandidate(
                        new UsernameRecoveryCandidate(SCHEME, "DB", "DBA", true,
                                "user-sch", delivery),
                        delivery == UsernameRecoveryDeliveryState.REQUESTED ? NOW : null,
                        delivery == UsernameRecoveryDeliveryState.REQUESTED ? "sender-ref" : null)));
    }

    private static UsernameRecoveryWorkflowState partiallyRequestedState() {
        UsernameRecoverySessionCandidate requested = new UsernameRecoverySessionCandidate(
                new UsernameRecoveryCandidate(SCHEME, "DB", "DBA", true,
                        "user-sch", UsernameRecoveryDeliveryState.REQUESTED),
                NOW,
                "sender-ref");
        UsernameRecoverySessionCandidate available = new UsernameRecoverySessionCandidate(
                new UsernameRecoveryCandidate(new UsernameRecoverySchemeRef("sch_available"),
                        "HK", "HKA", true, "user-other",
                        UsernameRecoveryDeliveryState.AVAILABLE),
                null,
                null);
        return new UsernameRecoveryWorkflowState(ID,
                UsernameRecoveryWorkflowStatus.PARTIALLY_REQUESTED,
                new UsernameRecoveryWorkflowStateRevision(0),
                new UsernameRecoveryVerifiedDestination("p****c@example.com",
                        UsernameRecoveryVerifiedDestination.Channel.EMAIL),
                Locale.ENGLISH,
                List.of(requested, available));
    }

    private static UsernameRecoveryWorkflowState stateWithRegistration(
            boolean registered, UsernameRecoveryDeliveryState delivery) {
        return new UsernameRecoveryWorkflowState(ID, UsernameRecoveryWorkflowStatus.ACTIVE,
                new UsernameRecoveryWorkflowStateRevision(0),
                new UsernameRecoveryVerifiedDestination("p****c@example.com",
                        UsernameRecoveryVerifiedDestination.Channel.EMAIL),
                Locale.ENGLISH,
                List.of(
                        new UsernameRecoverySessionCandidate(
                                new UsernameRecoveryCandidate(SCHEME, "DB", "DBA", registered,
                                        registered ? "user-sch" : null, delivery), null, null),
                        new UsernameRecoverySessionCandidate(
                                new UsernameRecoveryCandidate(new UsernameRecoverySchemeRef("sch_registered"),
                                        "HK", "HKA", true, "user-other",
                                        UsernameRecoveryDeliveryState.AVAILABLE), null, null)));
    }

    private static final class FakeAuthFlowPort implements AuthFlowSessionPort {
        private AuthFlowSessionSnapshot snapshot;
        private boolean fail;
        private int loads;
        @Override public Mono<Optional<AuthFlowSessionSnapshot>> loadSnapshot(AuthFlowSessionId id) {
            loads++;
            return fail ? Mono.error(new IllegalStateException("load failed"))
                    : Mono.just(Optional.ofNullable(snapshot));
        }
        @Override public Mono<AuthFlowSession> create(
                com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionCreateCommand c) {
            return Mono.error(new UnsupportedOperationException());
        }
        @Override public Mono<Optional<AuthFlowSession>> load(AuthFlowSessionId id) {
            return Mono.just(Optional.ofNullable(snapshot).map(AuthFlowSessionSnapshot::session));
        }
    }

    private static final class FakeWorkflowPort implements UsernameRecoveryWorkflowStatePort {
        private UsernameRecoveryWorkflowState state;
        private UsernameRecoveryWorkflowStateMutationOutcome updateOutcome =
                UsernameRecoveryWorkflowStateMutationOutcome.APPLIED;
        private boolean loadFail;
        private boolean updateFail;
        private int loads;
        private int updates;
        private UsernameRecoveryWorkflowStateMutationCommand lastMutation;
        @Override public Mono<UsernameRecoveryWorkflowStateLoadResult> load(AuthFlowSessionId id) {
            loads++;
            if (loadFail) return Mono.error(new IllegalStateException("load failed"));
            return Mono.just(state == null ? UsernameRecoveryWorkflowStateLoadResult.notFound()
                    : UsernameRecoveryWorkflowStateLoadResult.loaded(state));
        }
        @Override public Mono<UsernameRecoveryWorkflowStateMutationResult> update(
                UsernameRecoveryWorkflowStateMutationCommand command) {
            updates++;
            lastMutation = command;
            if (updateFail) return Mono.error(new IllegalStateException("update failed"));
            if (updateOutcome == UsernameRecoveryWorkflowStateMutationOutcome.APPLIED) {
                state = command.updatedState();
                return Mono.just(new UsernameRecoveryWorkflowStateMutationResult(updateOutcome, state));
            }
            return Mono.just(UsernameRecoveryWorkflowStateMutationResult.outcome(updateOutcome));
        }
        @Override public Mono<UsernameRecoveryWorkflowStateMutationResult> create(
                com.bct.ngtpa.apiservice.application.port.out.UsernameRecoveryWorkflowStateCreateCommand c) {
            return Mono.error(new UnsupportedOperationException());
        }
        @Override public Mono<UsernameRecoveryWorkflowStateDeleteResult> delete(AuthFlowSessionId id) {
            return Mono.error(new UnsupportedOperationException());
        }
    }
}
