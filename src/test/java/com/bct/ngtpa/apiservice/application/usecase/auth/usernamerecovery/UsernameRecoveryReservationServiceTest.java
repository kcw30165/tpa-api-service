package com.bct.ngtpa.apiservice.application.usecase.auth.usernamerecovery;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.application.port.out.UsernameRecoverySessionDeleteResult;
import com.bct.ngtpa.apiservice.application.port.out.UsernameRecoverySessionLoadResult;
import com.bct.ngtpa.apiservice.application.port.out.UsernameRecoverySessionMutationCommand;
import com.bct.ngtpa.apiservice.application.port.out.UsernameRecoverySessionMutationOutcome;
import com.bct.ngtpa.apiservice.application.port.out.UsernameRecoverySessionMutationResult;
import com.bct.ngtpa.apiservice.application.port.out.UsernameRecoverySessionPort;
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
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * Verifies the Username Recovery candidate reservation boundary: typed session load and expiry,
 * CSRF verification without raw-token retention, Scheme-Ref ownership and eligibility, pure
 * aggregate reservation, expected-revision compare-and-set persistence, and distinct conflict and
 * infrastructure-failure mapping.
 *
 * <p>Design references: {@code docs/design/6-username-recovery/username-recovery-api-contract-design.md}
 * section 8 (UR-REQ-001..011) and section 7 (HTTP mapping rules) and
 * {@code docs/design/6-username-recovery/username-recovery-session-redis-design.md} sections 2-4
 * (candidate reservation before sender acceptance; revision persisted consistently).
 */
class UsernameRecoveryReservationServiceTest {

    private static final UsernameRecoverySessionId SESSION_ID =
            new UsernameRecoverySessionId("afs_opaque-1");
    private static final Instant NOW = Instant.parse("2026-01-01T00:05:00Z");
    private static final Instant CREATED_AT = Instant.parse("2026-01-01T00:00:00Z");
    private static final Instant EXPIRES_AT = Instant.parse("2026-01-01T00:10:00Z");
    private static final Instant REQUESTED_AT = Instant.parse("2026-01-01T00:04:00Z");
    private static final String RAW_TOKEN = "raw-csrf-token-abc";
    private static final UsernameRecoveryCsrfHash CSRF_HASH =
            new UsernameRecoveryCsrfHash(new Sha256CsrfTokenHasher().hash(RAW_TOKEN));
    private static final UsernameRecoveryVerifiedDestination DESTINATION =
            new UsernameRecoveryVerifiedDestination(
                    "p****c@example.com", UsernameRecoveryVerifiedDestination.Channel.EMAIL);

    private final FakeSessionPort port = new FakeSessionPort();
    private final UsernameRecoveryReservationService service =
            new UsernameRecoveryReservationService(
                    port, new CsrfTokenValidationService(new Sha256CsrfTokenHasher()));

    @BeforeEach
    void resetPort() {
        port.clear();
    }

    private static UsernameRecoverySessionCandidate registered(
            String scheme, String env, String trust, UsernameRecoveryDeliveryState state) {
        return new UsernameRecoverySessionCandidate(
                new UsernameRecoveryCandidate(
                        new UsernameRecoverySchemeRef(scheme), env, trust, true, "user-" + scheme, state),
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

    private static UsernameRecoverySessionCandidate unregistered(
            String scheme, String env, String trust) {
        return new UsernameRecoverySessionCandidate(
                new UsernameRecoveryCandidate(
                        new UsernameRecoverySchemeRef(scheme),
                        env,
                        trust,
                        false,
                        null,
                        UsernameRecoveryDeliveryState.AVAILABLE),
                null,
                null);
    }

    private static UsernameRecoverySession session(
            UsernameRecoveryWorkflowStatus status,
            UsernameRecoverySessionRevision revision,
            Instant expiresAt,
            UsernameRecoverySessionCandidate... candidates) {
        return new UsernameRecoverySession(
                SESSION_ID,
                UsernameRecoverySession.Scenario.USERNAME_RECOVERY,
                status,
                revision,
                CSRF_HASH,
                DESTINATION,
                Locale.ENGLISH,
                CREATED_AT,
                expiresAt,
                List.of(candidates));
    }

    private static UsernameRecoverySession active() {
        return session(
                UsernameRecoveryWorkflowStatus.ACTIVE,
                new UsernameRecoverySessionRevision(0),
                EXPIRES_AT,
                registered("sch_1", "DB", "DBA", UsernameRecoveryDeliveryState.AVAILABLE));
    }

    private static UsernameRecoveryReservationCommand command(String schemeRef) {
        return new UsernameRecoveryReservationCommand(
                SESSION_ID, new UsernameRecoverySchemeRef(schemeRef), NOW);
    }

    @Test
    void reservesEligibleRegisteredCandidateWithExpectedRevisionCas() {
        port.store(active());

        StepVerifier.create(service.reserve(command("sch_1"), RAW_TOKEN))
                .assertNext(result -> {
                    assertThat(result.outcome()).isEqualTo(UsernameRecoveryReservationOutcome.RESERVED);
                    assertThat(result.toString())
                            .isEqualTo("UsernameRecoveryReservationResult[outcome=RESERVED]");
                })
                .verifyComplete();

        assertThat(port.updateCount).isEqualTo(1);
        assertThat(port.lastMutation().expectedRevision().value()).isEqualTo(0);
        assertThat(port.lastMutation().updatedSession().revision().value()).isEqualTo(1);
        assertThat(port.stored().candidates().get(0).deliveryState())
                .isEqualTo(UsernameRecoveryDeliveryState.RESERVED);
    }

    @Test
    void absentSessionMapsToTypedAbsentOutcome() {
        StepVerifier.create(service.reserve(command("sch_1"), RAW_TOKEN))
                .assertNext(result -> assertThat(result.outcome())
                        .isEqualTo(UsernameRecoveryReservationOutcome.SESSION_ABSENT))
                .verifyComplete();
        assertThat(port.updateCount).isZero();
    }

    @Test
    void expiredSessionMapsToExpiredOutcomeWithoutMutation() {
        port.store(session(
                UsernameRecoveryWorkflowStatus.ACTIVE,
                new UsernameRecoverySessionRevision(0),
                NOW,
                registered("sch_1", "DB", "DBA", UsernameRecoveryDeliveryState.AVAILABLE)));

        StepVerifier.create(service.reserve(command("sch_1"), RAW_TOKEN))
                .assertNext(result -> assertThat(result.outcome())
                        .isEqualTo(UsernameRecoveryReservationOutcome.SESSION_EXPIRED))
                .verifyComplete();
        assertThat(port.updateCount).isZero();
    }

    @Test
    void missingOrBlankCsrfTokenMapsToCsrfInvalidWithoutMutation() {
        port.store(active());

        StepVerifier.create(service.reserve(command("sch_1"), null))
                .assertNext(result -> assertThat(result.outcome())
                        .isEqualTo(UsernameRecoveryReservationOutcome.CSRF_INVALID))
                .verifyComplete();
        StepVerifier.create(service.reserve(command("sch_1"), " "))
                .assertNext(result -> assertThat(result.outcome())
                        .isEqualTo(UsernameRecoveryReservationOutcome.CSRF_INVALID))
                .verifyComplete();
        assertThat(port.updateCount).isZero();
    }

    @Test
    void mismatchedCsrfTokenMapsToCsrfInvalidWithoutMutation() {
        port.store(active());

        StepVerifier.create(service.reserve(command("sch_1"), "wrong-token"))
                .assertNext(result -> assertThat(result.outcome())
                        .isEqualTo(UsernameRecoveryReservationOutcome.CSRF_INVALID))
                .verifyComplete();
        assertThat(port.updateCount).isZero();
    }

    @Test
    void fabricatedSchemeRefMapsToSchemeRefInvalidWithoutMutation() {
        port.store(active());

        StepVerifier.create(service.reserve(command("sch_fabricated"), RAW_TOKEN))
                .assertNext(result -> assertThat(result.outcome())
                        .isEqualTo(UsernameRecoveryReservationOutcome.SCHEME_REF_INVALID))
                .verifyComplete();
        assertThat(port.updateCount).isZero();
    }

    @Test
    void unregisteredCandidateMapsToRegistrationRequiredWithoutMutation() {
        port.store(session(
                UsernameRecoveryWorkflowStatus.ACTIVE,
                new UsernameRecoverySessionRevision(0),
                EXPIRES_AT,
                registered("sch_1", "DB", "DBA", UsernameRecoveryDeliveryState.AVAILABLE),
                unregistered("sch_2", "DB", "DBB")));

        StepVerifier.create(service.reserve(command("sch_2"), RAW_TOKEN))
                .assertNext(result -> assertThat(result.outcome())
                        .isEqualTo(UsernameRecoveryReservationOutcome.REGISTRATION_REQUIRED))
                .verifyComplete();
        assertThat(port.updateCount).isZero();
    }

    @Test
    void requestedCandidateMapsToAlreadyRequestedWithoutMutation() {
        port.store(session(
                UsernameRecoveryWorkflowStatus.PARTIALLY_REQUESTED,
                new UsernameRecoverySessionRevision(0),
                EXPIRES_AT,
                requestedRegistered("sch_1", "DB", "DBA"),
                registered("sch_2", "DB", "DBB", UsernameRecoveryDeliveryState.AVAILABLE)));

        StepVerifier.create(service.reserve(command("sch_1"), RAW_TOKEN))
                .assertNext(result -> assertThat(result.outcome())
                        .isEqualTo(UsernameRecoveryReservationOutcome.ALREADY_REQUESTED))
                .verifyComplete();
        assertThat(port.updateCount).isZero();
    }

    @Test
    void terminalSessionsMapToWorkflowTerminalWithoutMutation() {
        List<UsernameRecoveryWorkflowStatus> terminal = List.of(
                UsernameRecoveryWorkflowStatus.COMPLETED,
                UsernameRecoveryWorkflowStatus.CANCELLED,
                UsernameRecoveryWorkflowStatus.EXPIRED);
        for (UsernameRecoveryWorkflowStatus status : terminal) {
            port.clear();
            UsernameRecoverySessionCandidate candidate = status == UsernameRecoveryWorkflowStatus.COMPLETED
                    ? requestedRegistered("sch_1", "DB", "DBA")
                    : registered("sch_1", "DB", "DBA", UsernameRecoveryDeliveryState.AVAILABLE);
            port.store(session(status, new UsernameRecoverySessionRevision(0), EXPIRES_AT, candidate));

            StepVerifier.create(service.reserve(command("sch_1"), RAW_TOKEN))
                    .assertNext(result -> assertThat(result.outcome())
                            .isEqualTo(UsernameRecoveryReservationOutcome.WORKFLOW_TERMINAL))
                    .verifyComplete();
            assertThat(port.updateCount).isZero();
        }
    }

    @Test
    void reservedFailedAndUnknownOutcomeCandidatesMapToCandidateNotAvailable() {
        for (UsernameRecoveryDeliveryState state : List.of(
                UsernameRecoveryDeliveryState.RESERVED,
                UsernameRecoveryDeliveryState.FAILED,
                UsernameRecoveryDeliveryState.UNKNOWN_OUTCOME)) {
            port.clear();
            port.store(session(
                    UsernameRecoveryWorkflowStatus.ACTIVE,
                    new UsernameRecoverySessionRevision(0),
                    EXPIRES_AT,
                    registered("sch_1", "DB", "DBA", state)));

            StepVerifier.create(service.reserve(command("sch_1"), RAW_TOKEN))
                    .assertNext(result -> assertThat(result.outcome())
                            .isEqualTo(UsernameRecoveryReservationOutcome.CANDIDATE_NOT_AVAILABLE))
                    .verifyComplete();
            assertThat(port.updateCount).isZero();
        }
    }

    @Test
    void revisionConflictMapsToDistinctConcurrencyOutcome() {
        port.store(active());
        port.updateOutcomeOverride = UsernameRecoverySessionMutationOutcome.REVISION_CONFLICT;

        StepVerifier.create(service.reserve(command("sch_1"), RAW_TOKEN))
                .assertNext(result -> assertThat(result.outcome())
                        .isEqualTo(UsernameRecoveryReservationOutcome.REVISION_CONFLICT))
                .verifyComplete();
        assertThat(port.updateCount).isEqualTo(1);
    }

    @Test
    void sessionNotFoundOnUpdateMapsToAbsentOutcome() {
        port.store(active());
        port.updateOutcomeOverride = UsernameRecoverySessionMutationOutcome.SESSION_NOT_FOUND;

        StepVerifier.create(service.reserve(command("sch_1"), RAW_TOKEN))
                .assertNext(result -> assertThat(result.outcome())
                        .isEqualTo(UsernameRecoveryReservationOutcome.SESSION_ABSENT))
                .verifyComplete();
    }

    @Test
    void loadSystemFailureOutcomeMapsToSystemFailed() {
        port.store(active());
        port.loadOutcomeOverride = UsernameRecoverySessionMutationOutcome.SYSTEM_FAILURE;

        StepVerifier.create(service.reserve(command("sch_1"), RAW_TOKEN))
                .assertNext(result -> assertThat(result.outcome())
                        .isEqualTo(UsernameRecoveryReservationOutcome.SYSTEM_FAILED))
                .verifyComplete();
        assertThat(port.updateCount).isZero();
    }

    @Test
    void loadInfrastructureErrorMapsToSystemFailed() {
        port.store(active());
        port.loadFails = true;

        StepVerifier.create(service.reserve(command("sch_1"), RAW_TOKEN))
                .assertNext(result -> assertThat(result.outcome())
                        .isEqualTo(UsernameRecoveryReservationOutcome.SYSTEM_FAILED))
                .verifyComplete();
        assertThat(port.updateCount).isZero();
    }

    @Test
    void updateSystemFailureOutcomeMapsToSystemFailed() {
        port.store(active());
        port.updateOutcomeOverride = UsernameRecoverySessionMutationOutcome.SYSTEM_FAILURE;

        StepVerifier.create(service.reserve(command("sch_1"), RAW_TOKEN))
                .assertNext(result -> assertThat(result.outcome())
                        .isEqualTo(UsernameRecoveryReservationOutcome.SYSTEM_FAILED))
                .verifyComplete();
    }

    @Test
    void updateInfrastructureErrorMapsToSystemFailed() {
        port.store(active());
        port.updateFails = true;

        StepVerifier.create(service.reserve(command("sch_1"), RAW_TOKEN))
                .assertNext(result -> assertThat(result.outcome())
                        .isEqualTo(UsernameRecoveryReservationOutcome.SYSTEM_FAILED))
                .verifyComplete();
    }

    @Test
    void rawTokenNeverRetainedInResultsOrCapturedMutation() {
        port.store(active());

        StepVerifier.create(service.reserve(command("sch_1"), RAW_TOKEN))
                .assertNext(result -> {
                    assertThat(result.outcome()).isEqualTo(UsernameRecoveryReservationOutcome.RESERVED);
                    assertThat(result.toString()).doesNotContain(RAW_TOKEN);
                    assertThat(UsernameRecoveryReservationResult.class.getRecordComponents())
                            .extracting(java.lang.reflect.RecordComponent::getName)
                            .containsExactly("outcome");
                })
                .verifyComplete();

        assertThat(command("sch_1").toString())
                .doesNotContain(RAW_TOKEN)
                .doesNotContain("afs_opaque-1")
                .doesNotContain("sch_1");
        assertThat(port.lastMutation().toString())
                .doesNotContain(RAW_TOKEN)
                .doesNotContain("afs_opaque-1")
                .doesNotContain("sch_1")
                .doesNotContain("user-")
                .doesNotContain("p****c@example.com");
    }

    @Test
    void unexpectedProgrammingDefectPropagatesToCentralBoundary() {
        UsernameRecoverySessionPort defective = new UsernameRecoverySessionPort() {
            public Mono<UsernameRecoverySessionMutationResult> create(UsernameRecoverySession s) { return Mono.error(new AssertionError("defect")); }
            public Mono<UsernameRecoverySessionLoadResult> load(UsernameRecoverySessionId id) { throw new AssertionError("defect"); }
            public Mono<UsernameRecoverySessionMutationResult> update(UsernameRecoverySessionMutationCommand c) { return Mono.error(new AssertionError("defect")); }
            public Mono<UsernameRecoverySessionDeleteResult> delete(UsernameRecoverySessionId id) { return Mono.error(new AssertionError("defect")); }
        };
        UsernameRecoveryReservationService defectiveService = new UsernameRecoveryReservationService(
                defective, new CsrfTokenValidationService(new Sha256CsrfTokenHasher()));
        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                defectiveService.reserve(command("sch_1"), RAW_TOKEN))
                .isInstanceOf(AssertionError.class)
                .hasMessage("defect");
    }

    private static final class FakeSessionPort implements UsernameRecoverySessionPort {

        private UsernameRecoverySession stored;
        private boolean loadFails;
        private boolean updateFails;
        private UsernameRecoverySessionMutationOutcome loadOutcomeOverride;
        private UsernameRecoverySessionMutationOutcome updateOutcomeOverride;
        private UsernameRecoverySessionMutationCommand lastMutation;
        private int updateCount;

        void store(UsernameRecoverySession session) {
            this.stored = session;
        }

        void clear() {
            stored = null;
            loadFails = false;
            updateFails = false;
            loadOutcomeOverride = null;
            updateOutcomeOverride = null;
            lastMutation = null;
            updateCount = 0;
        }

        UsernameRecoverySession stored() {
            return stored;
        }

        UsernameRecoverySessionMutationCommand lastMutation() {
            return lastMutation;
        }

        @Override
        public Mono<UsernameRecoverySessionMutationResult> create(UsernameRecoverySession session) {
            return Mono.error(new UnsupportedOperationException("create is not part of reservation"));
        }

        @Override
        public Mono<UsernameRecoverySessionLoadResult> load(UsernameRecoverySessionId sessionId) {
            if (loadFails) {
                return Mono.error(new IllegalStateException("load infrastructure failure"));
            }
            if (loadOutcomeOverride == UsernameRecoverySessionMutationOutcome.SYSTEM_FAILURE) {
                return Mono.just(UsernameRecoverySessionLoadResult.systemFailure());
            }
            if (stored == null) {
                return Mono.just(UsernameRecoverySessionLoadResult.notFound());
            }
            return Mono.just(UsernameRecoverySessionLoadResult.loaded(stored));
        }

        @Override
        public Mono<UsernameRecoverySessionMutationResult> update(UsernameRecoverySessionMutationCommand command) {
            updateCount++;
            lastMutation = command;
            if (updateFails) {
                return Mono.error(new IllegalStateException("update infrastructure failure"));
            }
            if (updateOutcomeOverride != null) {
                return Mono.just(UsernameRecoverySessionMutationResult.outcome(updateOutcomeOverride));
            }
            if (stored == null) {
                return Mono.just(UsernameRecoverySessionMutationResult.outcome(
                        UsernameRecoverySessionMutationOutcome.SESSION_NOT_FOUND));
            }
            if (!stored.revision().equals(command.expectedRevision())) {
                return Mono.just(UsernameRecoverySessionMutationResult.outcome(
                        UsernameRecoverySessionMutationOutcome.REVISION_CONFLICT));
            }
            stored = command.updatedSession();
            return Mono.just(new UsernameRecoverySessionMutationResult(
                    UsernameRecoverySessionMutationOutcome.APPLIED, stored));
        }

        @Override
        public Mono<UsernameRecoverySessionDeleteResult> delete(UsernameRecoverySessionId sessionId) {
            return Mono.error(new UnsupportedOperationException("delete is not part of reservation"));
        }
    }
}
