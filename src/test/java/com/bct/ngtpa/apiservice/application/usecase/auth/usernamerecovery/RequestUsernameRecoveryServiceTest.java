package com.bct.ngtpa.apiservice.application.usecase.auth.usernamerecovery;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.application.dto.auth.usernamerecovery.UsernameRecoveryDestinationType;
import com.bct.ngtpa.apiservice.application.dto.auth.usernamerecovery.UsernameRecoveryEffectiveDestinationCommand;
import com.bct.ngtpa.apiservice.application.dto.auth.usernamerecovery.UsernameRecoveryEffectiveDestinationResult;
import com.bct.ngtpa.apiservice.application.dto.auth.usernamerecovery.UsernameRecoveryRequestCommand;
import com.bct.ngtpa.apiservice.application.dto.auth.usernamerecovery.UsernameRecoveryRequestResult;
import com.bct.ngtpa.apiservice.application.dto.message.MessageDeliveryChannel;
import com.bct.ngtpa.apiservice.application.dto.message.MessageDeliveryCommand;
import com.bct.ngtpa.apiservice.application.dto.message.MessageDeliveryResult;
import com.bct.ngtpa.apiservice.application.dto.message.MessageDeliveryScenario;
import com.bct.ngtpa.apiservice.application.dto.message.MessageDeliveryStatus;
import com.bct.ngtpa.apiservice.application.dto.message.MessageDeliveryType;
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
import com.bct.ngtpa.apiservice.application.port.out.MessageDeliveryPort;
import com.bct.ngtpa.apiservice.application.port.out.UsernameRecoveryEffectiveDestinationPort;
import com.bct.ngtpa.apiservice.application.port.out.UsernameRecoveryIdempotencyAction;
import com.bct.ngtpa.apiservice.application.port.out.UsernameRecoveryIdempotencyFingerprint;
import com.bct.ngtpa.apiservice.application.port.out.UsernameRecoveryIdempotencyKey;
import com.bct.ngtpa.apiservice.application.port.out.UsernameRecoveryIdempotencyOperationReference;
import com.bct.ngtpa.apiservice.application.port.out.UsernameRecoveryIdempotencyOutcome;
import com.bct.ngtpa.apiservice.application.port.out.UsernameRecoveryIdempotencyPort;
import com.bct.ngtpa.apiservice.application.port.out.UsernameRecoveryIdempotencyRecord;
import com.bct.ngtpa.apiservice.application.port.out.UsernameRecoveryIdempotencyReservation;
import com.bct.ngtpa.apiservice.application.port.out.UsernameRecoveryWorkflowStateDeleteResult;
import com.bct.ngtpa.apiservice.application.port.out.UsernameRecoveryWorkflowStateLoadResult;
import com.bct.ngtpa.apiservice.application.port.out.UsernameRecoveryWorkflowStateMutationCommand;
import com.bct.ngtpa.apiservice.application.port.out.UsernameRecoveryWorkflowStateMutationOutcome;
import com.bct.ngtpa.apiservice.application.port.out.UsernameRecoveryWorkflowStateMutationResult;
import com.bct.ngtpa.apiservice.application.port.out.UsernameRecoveryWorkflowStatePort;
import com.bct.ngtpa.apiservice.application.usecase.auth.usernamerecovery.UsernameRecoveryRequestException.Reason;
import com.bct.ngtpa.apiservice.application.usecase.session.authflow.CsrfTokenValidationService;
import com.bct.ngtpa.apiservice.application.usecase.session.authflow.Sha256CsrfTokenHasher;
import com.bct.ngtpa.apiservice.domain.model.UsernameRecoveryCandidate;
import com.bct.ngtpa.apiservice.domain.model.UsernameRecoveryDeliveryState;
import com.bct.ngtpa.apiservice.domain.model.UsernameRecoverySchemeRef;
import com.bct.ngtpa.apiservice.domain.model.UsernameRecoverySessionCandidate;
import com.bct.ngtpa.apiservice.domain.model.UsernameRecoveryVerifiedDestination;
import com.bct.ngtpa.apiservice.domain.model.UsernameRecoveryWorkflowStatus;
import java.time.Clock;
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
 * Verifies the Task 020 {@link RequestUsernameRecoveryService} orchestration:
 * validation, semantic
 * fingerprinting, REQUEST idempotency classification, origin/referer
 * enforcement, candidate
 * reservation mapping, effective-destination resolution, at-most-once localized
 * sender invocation,
 * accepted/definite-failure/unknown delivery handling, post-acceptance
 * persistence safety, and
 * completed replay without resending.
 *
 * <p>
 * Design references: {@code .cline/tasks/020-request-use-case.md} and
 * {@code docs/design/6-username-recovery/username-recovery-api-contract-design.md}
 * section 8
 * (UR-REQ-001..011).
 */
class RequestUsernameRecoveryServiceTest {

        private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");
        private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
        private static final int TTL_SECONDS = 600;
        private static final String IDEM_KEY = "idem_12345678";
        private static final String CSRF = "csrf-token-abc";
        private static final String SESSION_ID = "afs_opaque-1";
        private static final String REGISTERED_REF = "sch_opaque-1";
        private static final String UNREGISTERED_REF = "sch_opaque-2";
        private static final String EMAIL_SUBJECT = "BCT username recovery";
        private static final String EMAIL_BODY = "Your BCT username is {username}.";
        private static final String SMS_BODY = "Your BCT username is {username}.";

        private final List<String> callLog = new ArrayList<>();
        private final FakeIdempotencyPort idempotencyPort = new FakeIdempotencyPort(callLog);
        private final FakeAuthFlowPort envelopes = new FakeAuthFlowPort();
        private final FakeWorkflowPort workflows = new FakeWorkflowPort();
        private final FakeEffectiveDestinationPort effectiveDestinationPort = new FakeEffectiveDestinationPort();
        private final FakeMessageDeliveryPort messageDeliveryPort = new FakeMessageDeliveryPort();

        private UsernameRecoveryReservationService reservationService;
        private RequestUsernameRecoveryService service;

        @BeforeEach
        void setUp() {
                storeScenario(activeWorkflowState());
                reservationService = new UsernameRecoveryReservationService(
                                envelopes, workflows, new CsrfTokenValidationService(new Sha256CsrfTokenHasher()));
                service = newService(List.of());
        }

        private RequestUsernameRecoveryService newService(List<String> allowedOrigins) {
                return new RequestUsernameRecoveryService(
                                new UsernameRecoveryRequestFingerprintGenerator(),
                                idempotencyPort,
                                reservationService,
                                effectiveDestinationPort,
                                envelopes,
                                workflows,
                                messageDeliveryPort,
                                allowedOrigins,
                                CLOCK,
                                TTL_SECONDS,
                                EMAIL_SUBJECT,
                                EMAIL_BODY,
                                SMS_BODY);
        }

        @Test
        void nullCommandMapsToTypedValidationFailureWithoutSideEffects() {
                StepVerifier.create(service.request(null, CSRF, null, null))
                                .expectErrorSatisfies(error -> {
                                        assertThat(error).isInstanceOf(UsernameRecoveryRequestException.class);
                                        assertThat(((UsernameRecoveryRequestException) error).reason())
                                                        .isEqualTo(Reason.VALIDATION_FAILED);
                                })
                                .verify();

                assertThat(callLog).isEmpty();
                assertThat(messageDeliveryPort.deliverCalls).isZero();
        }

        @Test
        void invalidIdempotencyKeyMapsToTypedValidationFailureWithoutReservation() {
                UsernameRecoveryRequestCommand command = new UsernameRecoveryRequestCommand(
                                new AuthFlowSessionId(SESSION_ID),
                                new UsernameRecoverySchemeRef(REGISTERED_REF),
                                "bad key with space");

                StepVerifier.create(service.request(command, CSRF, null, null))
                                .expectErrorSatisfies(error -> {
                                        assertThat(error).isInstanceOf(UsernameRecoveryRequestException.class);
                                        assertThat(((UsernameRecoveryRequestException) error).reason())
                                                        .isEqualTo(Reason.VALIDATION_FAILED);
                                })
                                .verify();

                assertThat(callLog).isEmpty();
                assertThat(messageDeliveryPort.deliverCalls).isZero();
        }

        @Test
        void committedPortBoundaryRequestFailsClosedWithoutSecurityContext() {
                StepVerifier.create(service.request(command()))
                                .expectErrorSatisfies(error -> {
                                        assertThat(error).isInstanceOf(UsernameRecoveryRequestException.class);
                                        assertThat(((UsernameRecoveryRequestException) error).reason())
                                                        .isEqualTo(Reason.SECURITY_CONTEXT_REQUIRED);
                                })
                                .verify();

                assertThat(callLog).isEmpty();
                assertThat(messageDeliveryPort.deliverCalls).isZero();
        }

        @Test
        void duplicateInProgressMapsToTypedSignalWithoutDelivery() {
                idempotencyPort.reserveOutcome = UsernameRecoveryIdempotencyOutcome.DUPLICATE_IN_PROGRESS;

                StepVerifier.create(service.request(command(), CSRF, null, null))
                                .expectErrorSatisfies(error -> {
                                        assertThat(error).isInstanceOf(UsernameRecoveryRequestException.class);
                                        assertThat(((UsernameRecoveryRequestException) error).reason())
                                                        .isEqualTo(Reason.IDEMPOTENCY_IN_PROGRESS);
                                })
                                .verify();

                assertThat(callLog).containsExactly("idem.reserve");
                assertThat(messageDeliveryPort.deliverCalls).isZero();
        }

        @Test
        void keyReusedMapsToTypedSignalWithoutDelivery() {
                idempotencyPort.reserveOutcome = UsernameRecoveryIdempotencyOutcome.KEY_REUSED_WITH_DIFFERENT_REQUEST;

                StepVerifier.create(service.request(command(), CSRF, null, null))
                                .expectErrorSatisfies(error -> {
                                        assertThat(error).isInstanceOf(UsernameRecoveryRequestException.class);
                                        assertThat(((UsernameRecoveryRequestException) error).reason())
                                                        .isEqualTo(Reason.IDEMPOTENCY_KEY_REUSED);
                                })
                                .verify();

                assertThat(callLog).containsExactly("idem.reserve");
                assertThat(messageDeliveryPort.deliverCalls).isZero();
        }

        @Test
        void unknownOutcomeExistingMapsToTypedSignalWithoutDelivery() {
                idempotencyPort.reserveOutcome = UsernameRecoveryIdempotencyOutcome.UNKNOWN_OUTCOME_EXISTING;

                StepVerifier.create(service.request(command(), CSRF, null, null))
                                .expectErrorSatisfies(error -> {
                                        assertThat(error).isInstanceOf(UsernameRecoveryRequestException.class);
                                        assertThat(((UsernameRecoveryRequestException) error).reason())
                                                        .isEqualTo(Reason.UNKNOWN_OUTCOME_EXISTING);
                                })
                                .verify();

                assertThat(callLog).containsExactly("idem.reserve");
                assertThat(messageDeliveryPort.deliverCalls).isZero();
        }

        @Test
        void reserveSystemFailureMapsToTypedSystemFailure() {
                idempotencyPort.reserveOutcome = UsernameRecoveryIdempotencyOutcome.SYSTEM_FAILURE;

                StepVerifier.create(service.request(command(), CSRF, null, null))
                                .expectErrorSatisfies(error -> {
                                        assertThat(error).isInstanceOf(UsernameRecoveryRequestException.class);
                                        assertThat(((UsernameRecoveryRequestException) error).reason())
                                                        .isEqualTo(Reason.SYSTEM_FAILURE);
                                })
                                .verify();

                assertThat(callLog).containsExactly("idem.reserve");
                assertThat(messageDeliveryPort.deliverCalls).isZero();
        }

        @Test
        void reserveInfrastructureErrorMapsToTypedSystemFailure() {
                idempotencyPort.reserveFails = true;

                StepVerifier.create(service.request(command(), CSRF, null, null))
                                .expectErrorSatisfies(error -> {
                                        assertThat(error).isInstanceOf(UsernameRecoveryRequestException.class);
                                        assertThat(((UsernameRecoveryRequestException) error).reason())
                                                        .isEqualTo(Reason.SYSTEM_FAILURE);
                                })
                                .verify();

                assertThat(callLog).containsExactly("idem.reserve");
                assertThat(messageDeliveryPort.deliverCalls).isZero();
        }

        @Test
        void disallowedOriginFailsClosedBeforeReservationOrDelivery() {
                service = newService(List.of("https://app.example"));

                StepVerifier.create(service.request(command(), CSRF, "https://evil.example", null))
                                .expectErrorSatisfies(error -> {
                                        assertThat(error).isInstanceOf(UsernameRecoveryRequestException.class);
                                        assertThat(((UsernameRecoveryRequestException) error).reason())
                                                        .isEqualTo(Reason.SECURITY_INVALID);
                                })
                                .verify();

                assertThat(callLog).containsExactly("idem.reserve");
                assertThat(messageDeliveryPort.deliverCalls).isZero();
        }

        @Test
        void allowedOriginPassesAndDeliveryContinues() {
                service = newService(List.of("https://app.example"));

                StepVerifier.create(service.request(command(), CSRF, "https://app.example", null))
                                .assertNext(result -> assertThat(result.outcome())
                                                .isEqualTo(UsernameRecoveryRequestResult.Outcome.REQUEST_ACCEPTED))
                                .verifyComplete();

                assertThat(messageDeliveryPort.deliverCalls).isEqualTo(1);
        }

        @Test
        void sessionAbsentMapsToTypedSessionAbsentWithoutDelivery() {
                envelopes.snapshot = null;
                workflows.state = null;

                StepVerifier.create(service.request(command(), CSRF, null, null))
                                .expectErrorSatisfies(error -> {
                                        assertThat(error).isInstanceOf(UsernameRecoveryRequestException.class);
                                        assertThat(((UsernameRecoveryRequestException) error).reason())
                                                        .isEqualTo(Reason.SESSION_ABSENT);
                                })
                                .verify();

                assertThat(messageDeliveryPort.deliverCalls).isZero();
        }

        @Test
        void sessionExpiredMapsToTypedSessionExpiredWithoutDelivery() {
                UsernameRecoveryWorkflowState stored = activeWorkflowState();
                envelopes.snapshot = replayEnvelope(
                                stored.authFlowSessionId().value(),
                                AuthFlowScenario.USERNAME_RECOVERY,
                                AuthFlowStatus.STARTED,
                                NOW.minusSeconds(1));
                workflows.state = stored;

                StepVerifier.create(service.request(command(), CSRF, null, null))
                                .expectErrorSatisfies(error -> {
                                        assertThat(error).isInstanceOf(UsernameRecoveryRequestException.class);
                                        assertThat(((UsernameRecoveryRequestException) error).reason())
                                                        .isEqualTo(Reason.SESSION_EXPIRED);
                                })
                                .verify();

                assertThat(messageDeliveryPort.deliverCalls).isZero();
        }

        @Test
        void csrfMismatchMapsToTypedSecurityInvalidWithoutDelivery() {
                StepVerifier.create(service.request(command(), "wrong-csrf-token", null, null))
                                .expectErrorSatisfies(error -> {
                                        assertThat(error).isInstanceOf(UsernameRecoveryRequestException.class);
                                        assertThat(((UsernameRecoveryRequestException) error).reason())
                                                        .isEqualTo(Reason.SECURITY_INVALID);
                                })
                                .verify();

                assertThat(messageDeliveryPort.deliverCalls).isZero();
        }

        @Test
        void invalidSchemeRefReturnsTypedResultWithoutDelivery() {
                UsernameRecoveryRequestCommand command = new UsernameRecoveryRequestCommand(
                                new AuthFlowSessionId(SESSION_ID),
                                new UsernameRecoverySchemeRef("sch_fabricated"),
                                IDEM_KEY);

                StepVerifier.create(service.request(command, CSRF, null, null))
                                .assertNext(result -> assertThat(result.outcome())
                                                .isEqualTo(UsernameRecoveryRequestResult.Outcome.INVALID_SCHEME_REF))
                                .verifyComplete();

                assertThat(messageDeliveryPort.deliverCalls).isZero();
        }

        @Test
        void unregisteredCandidateReturnsTypedResultWithoutDelivery() {
                UsernameRecoveryRequestCommand command = new UsernameRecoveryRequestCommand(
                                new AuthFlowSessionId(SESSION_ID),
                                new UsernameRecoverySchemeRef(UNREGISTERED_REF),
                                IDEM_KEY);

                StepVerifier.create(service.request(command, CSRF, null, null))
                                .assertNext(result -> assertThat(result.outcome())
                                                .isEqualTo(UsernameRecoveryRequestResult.Outcome.UNREGISTERED_CANDIDATE))
                                .verifyComplete();

                assertThat(messageDeliveryPort.deliverCalls).isZero();
        }

        @Test
        void alreadyRequestedCandidateReturnsTypedResultWithoutResending() {
                storeScenario(sessionWithCandidateState(
                                UsernameRecoveryDeliveryState.REQUESTED,
                                UsernameRecoveryWorkflowStatus.PARTIALLY_REQUESTED));

                StepVerifier.create(service.request(command(), CSRF, null, null))
                                .assertNext(result -> assertThat(result.outcome())
                                                .isEqualTo(UsernameRecoveryRequestResult.Outcome.ALREADY_REQUESTED))
                                .verifyComplete();

                assertThat(messageDeliveryPort.deliverCalls).isZero();
        }

        @Test
        void completedWorkflowReturnsTypedCompletedResultWithoutMutation() {
                storeScenario(sessionWithCandidateState(
                                UsernameRecoveryDeliveryState.REQUESTED, UsernameRecoveryWorkflowStatus.COMPLETED));

                StepVerifier.create(service.request(command(), CSRF, null, null))
                                .assertNext(result -> assertThat(result.outcome())
                                                .isEqualTo(UsernameRecoveryRequestResult.Outcome.WORKFLOW_COMPLETED))
                                .verifyComplete();

                assertThat(messageDeliveryPort.deliverCalls).isZero();
        }

        @Test
        void reservationRevisionConflictMapsToTypedSignalWithoutDelivery() {
                workflows.updateOutcome = UsernameRecoveryWorkflowStateMutationOutcome.REVISION_CONFLICT;

                StepVerifier.create(service.request(command(), CSRF, null, null))
                                .expectErrorSatisfies(error -> {
                                        assertThat(error).isInstanceOf(UsernameRecoveryRequestException.class);
                                        assertThat(((UsernameRecoveryRequestException) error).reason())
                                                        .isEqualTo(Reason.REVISION_CONFLICT);
                                })
                                .verify();

                assertThat(messageDeliveryPort.deliverCalls).isZero();
        }

        @Test
        void candidateReservedByAnotherKeyMapsToCandidateNotAvailableWithoutDelivery() {
                storeScenario(sessionWithCandidateState(
                                UsernameRecoveryDeliveryState.RESERVED, UsernameRecoveryWorkflowStatus.ACTIVE));

                StepVerifier.create(service.request(command(), CSRF, null, null))
                                .expectErrorSatisfies(error -> {
                                        assertThat(error).isInstanceOf(UsernameRecoveryRequestException.class);
                                        assertThat(((UsernameRecoveryRequestException) error).reason())
                                                        .isEqualTo(Reason.CANDIDATE_NOT_AVAILABLE);
                                })
                                .verify();

                assertThat(messageDeliveryPort.deliverCalls).isZero();
        }

        @Test
        void acceptedDeliveryReservesMarksRequestedPersistsAndCompletes() {
                storeScenario(twoRegisteredSession());

                StepVerifier.create(service.request(command(), CSRF, null, null))
                                .assertNext(result -> {
                                        assertThat(result.outcome()).isEqualTo(
                                                        UsernameRecoveryRequestResult.Outcome.REQUEST_ACCEPTED);
                                        assertThat(result.workflowStatus())
                                                        .isEqualTo(UsernameRecoveryWorkflowStatus.PARTIALLY_REQUESTED);
                                        assertThat(result.schemeRef())
                                                        .isEqualTo(new UsernameRecoverySchemeRef(REGISTERED_REF));
                                        assertThat(result.maskedDestination()).isEqualTo("p****c@ah.ozk");
                                        assertThat(result.destinationType())
                                                        .isEqualTo(UsernameRecoveryDestinationType.EMAIL);
                                })
                                .verifyComplete();

                assertThat(messageDeliveryPort.deliverCalls).isEqualTo(1);
                assertThat(idempotencyPort.completeCalls).isEqualTo(1);
                assertThat(idempotencyPort.lastCompleted.replaySnapshot())
                                .isEqualTo(UsernameRecoveryRequestReplayCodec.encode(
                                                new AuthFlowSessionId(SESSION_ID)));

                UsernameRecoveryWorkflowState stored = workflows.state;
                UsernameRecoverySessionCandidate requested = stored.candidates().stream()
                                .filter(c -> c.schemeRef().value().equals(REGISTERED_REF))
                                .findFirst().orElseThrow();
                assertThat(requested.deliveryState()).isEqualTo(UsernameRecoveryDeliveryState.REQUESTED);
                assertThat(requested.requestedAt()).isEqualTo(NOW);
                assertThat(requested.senderReference()).isEqualTo("provider-msg-1");
                assertThat(stored.status()).isEqualTo(UsernameRecoveryWorkflowStatus.PARTIALLY_REQUESTED);
                assertThat(envelopes.snapshot.session().status()).isEqualTo(AuthFlowStatus.STARTED);
                assertThat(envelopes.completeCalls).isZero();
        }

        @Test
        void acceptedDeliveryCompletesWorkflowWhenAllRegisteredCandidatesAreRequested() {
                storeScenario(singleRegisteredSession());

                StepVerifier.create(service.request(command(), CSRF, null, null))
                                .assertNext(result -> {
                                        assertThat(result.outcome()).isEqualTo(
                                                        UsernameRecoveryRequestResult.Outcome.REQUEST_ACCEPTED);
                                        assertThat(result.workflowStatus())
                                                        .isEqualTo(UsernameRecoveryWorkflowStatus.COMPLETED);
                                })
                                .verifyComplete();

                assertThat(workflows.state.status()).isEqualTo(UsernameRecoveryWorkflowStatus.COMPLETED);
                assertThat(envelopes.snapshot.session().status()).isEqualTo(AuthFlowStatus.COMPLETED);
                assertThat(envelopes.snapshot.session().expiresAt()).isEqualTo(NOW.plusSeconds(TTL_SECONDS));
                assertThat(envelopes.completeCalls).isEqualTo(1);
                assertThat(messageDeliveryPort.deliverCalls).isEqualTo(1);
        }

        @Test
        void finalEnvelopeCompletionConflictRecordsUnknownOutcomeWithoutResend() {
                storeScenario(singleRegisteredSession());
                envelopes.completeOutcome = com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionMutationOutcome.SESSION_REVISION_CHANGED;

                StepVerifier.create(service.request(command(), CSRF, null, null))
                                .expectNext(new UsernameRecoveryRequestResult(
                                                UsernameRecoveryRequestResult.Outcome.UNKNOWN_OUTCOME,
                                                null, null, null, null))
                                .verifyComplete();

                assertThat(workflows.state.status()).isEqualTo(UsernameRecoveryWorkflowStatus.COMPLETED);
                assertThat(envelopes.snapshot.session().status()).isEqualTo(AuthFlowStatus.STARTED);
                assertThat(envelopes.completeCalls).isEqualTo(1);
                assertThat(messageDeliveryPort.deliverCalls).isEqualTo(1);
                assertThat(idempotencyPort.completeCalls).isZero();
                assertThat(idempotencyPort.unknownOutcomeCalls).isEqualTo(1);
        }

        @Test
        void deliveryCommandIsLocalizedUsernameRecoveryCommand() {
                StepVerifier.create(service.request(command(), CSRF, null, null))
                                .expectNextCount(1)
                                .verifyComplete();

                MessageDeliveryCommand delivery = messageDeliveryPort.lastCommand;
                assertThat(delivery.messageType()).isEqualTo(MessageDeliveryType.USERNAME_RECOVERY);
                assertThat(delivery.scenario()).isEqualTo(MessageDeliveryScenario.USERNAME_RECOVERY);
                assertThat(delivery.channel()).isEqualTo(MessageDeliveryChannel.EMAIL);
                assertThat(delivery.recipient()).isEqualTo("pepperc@ah.ozk");
                assertThat(delivery.subject()).isEqualTo(EMAIL_SUBJECT);
                assertThat(delivery.body()).isEqualTo(EMAIL_BODY);
                assertThat(delivery.templateVariables()).containsEntry("username", "user-1");
                assertThat(delivery.locale()).isEqualTo("en");
                assertThat(delivery.correlationId()).startsWith("op_");
        }

        @Test
        void senderFallsBackToOperationReferenceWhenProviderMessageIdIsMissing() {
                messageDeliveryPort.result = new MessageDeliveryResult(
                                MessageDeliveryStatus.ACCEPTED, MessageDeliveryChannel.EMAIL, null, null, null);

                StepVerifier.create(service.request(command(), CSRF, null, null))
                                .assertNext(result -> assertThat(result.outcome())
                                                .isEqualTo(UsernameRecoveryRequestResult.Outcome.REQUEST_ACCEPTED))
                                .verifyComplete();

                UsernameRecoverySessionCandidate requested = workflows.state.candidates().stream()
                                .filter(c -> c.schemeRef().value().equals(REGISTERED_REF))
                                .findFirst().orElseThrow();
                assertThat(requested.senderReference()).isNotBlank();
                assertThat(requested.senderReference()).startsWith("op_");
        }

        @Test
        void definiteDeliveryFailurePersistsFailedAndReturnsSystemFailedResult() {
                messageDeliveryPort.result = new MessageDeliveryResult(
                                MessageDeliveryStatus.FAILED, MessageDeliveryChannel.EMAIL, null, "REJECTED",
                                "rejected");

                StepVerifier.create(service.request(command(), CSRF, null, null))
                                .assertNext(result -> assertThat(result.outcome())
                                                .isEqualTo(UsernameRecoveryRequestResult.Outcome.SYSTEM_FAILED))
                                .verifyComplete();

                UsernameRecoverySessionCandidate failed = workflows.state.candidates().stream()
                                .filter(c -> c.schemeRef().value().equals(REGISTERED_REF))
                                .findFirst().orElseThrow();
                assertThat(failed.deliveryState()).isEqualTo(UsernameRecoveryDeliveryState.FAILED);
                assertThat(idempotencyPort.completeCalls).isEqualTo(1);
                assertThat(messageDeliveryPort.deliverCalls).isEqualTo(1);
        }

        @Test
        void unknownDeliveryOutcomePersistsStableUnknownAndReturnsUnknownResult() {
                messageDeliveryPort.result = new MessageDeliveryResult(
                                MessageDeliveryStatus.UNKNOWN_OUTCOME, MessageDeliveryChannel.EMAIL, null, "UNKNOWN",
                                "unknown");

                StepVerifier.create(service.request(command(), CSRF, null, null))
                                .assertNext(result -> assertThat(result.outcome())
                                                .isEqualTo(UsernameRecoveryRequestResult.Outcome.UNKNOWN_OUTCOME))
                                .verifyComplete();

                UsernameRecoverySessionCandidate unknown = workflows.state.candidates().stream()
                                .filter(c -> c.schemeRef().value().equals(REGISTERED_REF))
                                .findFirst().orElseThrow();
                assertThat(unknown.deliveryState()).isEqualTo(UsernameRecoveryDeliveryState.UNKNOWN_OUTCOME);
                assertThat(idempotencyPort.unknownOutcomeCalls).isEqualTo(1);
                assertThat(idempotencyPort.completeCalls).isZero();
                assertThat(messageDeliveryPort.deliverCalls).isEqualTo(1);
        }

        @Test
        void sessionCasConflictAfterAcceptanceRecordsStableUnknownOutcomeWithoutResend() {
                workflows.forceUpdateOutcomeAtCall = 2;
                workflows.forcedUpdateOutcome = UsernameRecoveryWorkflowStateMutationOutcome.REVISION_CONFLICT;

                StepVerifier.create(service.request(command(), CSRF, null, null))
                                .assertNext(result -> assertThat(result.outcome())
                                                .isEqualTo(UsernameRecoveryRequestResult.Outcome.UNKNOWN_OUTCOME))
                                .verifyComplete();

                assertThat(messageDeliveryPort.deliverCalls).isEqualTo(1);
                assertThat(idempotencyPort.unknownOutcomeCalls).isEqualTo(1);
                assertThat(idempotencyPort.completeCalls).isZero();
        }

        @Test
        void idempotencyCompleteFailureAfterAcceptanceRecordsStableUnknownOutcomeWithoutResend() {
                idempotencyPort.completeFails = true;

                StepVerifier.create(service.request(command(), CSRF, null, null))
                                .assertNext(result -> assertThat(result.outcome())
                                                .isEqualTo(UsernameRecoveryRequestResult.Outcome.UNKNOWN_OUTCOME))
                                .verifyComplete();

                assertThat(messageDeliveryPort.deliverCalls).isEqualTo(1);
                assertThat(idempotencyPort.unknownOutcomeCalls).isEqualTo(1);
                assertThat(workflows.state.candidates().stream()
                                .filter(c -> c.schemeRef().value().equals(REGISTERED_REF))
                                .findFirst().orElseThrow().deliveryState())
                                .isEqualTo(UsernameRecoveryDeliveryState.REQUESTED);
        }

        @Test
        void unknownOutcomePersistenceFailureMapsToTypedSystemFailure() {
                idempotencyPort.unknownOutcomeFails = true;
                messageDeliveryPort.result = new MessageDeliveryResult(
                                MessageDeliveryStatus.UNKNOWN_OUTCOME, MessageDeliveryChannel.EMAIL, null, "UNKNOWN",
                                "unknown");

                StepVerifier.create(service.request(command(), CSRF, null, null))
                                .expectErrorSatisfies(error -> {
                                        assertThat(error).isInstanceOf(UsernameRecoveryRequestException.class);
                                        assertThat(((UsernameRecoveryRequestException) error).reason())
                                                        .isEqualTo(Reason.SYSTEM_FAILURE);
                                })
                                .verify();

                assertThat(messageDeliveryPort.deliverCalls).isEqualTo(1);
        }

        @Test
        void effectiveDestinationResolutionFailurePersistsDefiniteFailureBeforeSenderInvocation() {
                effectiveDestinationPort.fails = true;
                StepVerifier.create(service.request(command(), CSRF, null, null))
                                .assertNext(result -> assertThat(result.outcome())
                                                .isEqualTo(UsernameRecoveryRequestResult.Outcome.SYSTEM_FAILED))
                                .verifyComplete();
                assertThat(messageDeliveryPort.deliverCalls).isZero();
                assertThat(idempotencyPort.completeCalls).isEqualTo(1);
                assertThat(workflows.state.candidates().stream()
                                .filter(c -> c.schemeRef().value().equals(REGISTERED_REF))
                                .findFirst().orElseThrow().deliveryState())
                                .isEqualTo(UsernameRecoveryDeliveryState.FAILED);
        }

        @Test
        void reactiveDeliveryFailurePersistsStableUnknownOutcomeWithoutResend() {
                messageDeliveryPort.fails = true;
                StepVerifier.create(service.request(command(), CSRF, null, null))
                                .assertNext(result -> assertThat(result.outcome())
                                                .isEqualTo(UsernameRecoveryRequestResult.Outcome.UNKNOWN_OUTCOME))
                                .verifyComplete();
                assertThat(messageDeliveryPort.deliverCalls).isEqualTo(1);
                assertThat(idempotencyPort.unknownOutcomeCalls).isEqualTo(1);
                assertThat(idempotencyPort.completeCalls).isZero();
                assertThat(workflows.state.candidates().stream()
                                .filter(c -> c.schemeRef().value().equals(REGISTERED_REF))
                                .findFirst().orElseThrow().deliveryState())
                                .isEqualTo(UsernameRecoveryDeliveryState.UNKNOWN_OUTCOME);
        }

        @Test
        void nonProductionEffectiveOverrideIsUsedOnlyAsRecipientAndOriginalIsMasked() {
                effectiveDestinationPort.result = new UsernameRecoveryEffectiveDestinationResult(
                                "override@example.test", UsernameRecoveryVerifiedDestination.Channel.EMAIL);

                StepVerifier.create(service.request(command(), CSRF, null, null))
                                .assertNext(result -> {
                                        assertThat(result.outcome()).isEqualTo(
                                                        UsernameRecoveryRequestResult.Outcome.REQUEST_ACCEPTED);
                                        assertThat(result.maskedDestination()).isEqualTo("p****c@ah.ozk");
                                })
                                .verifyComplete();

                assertThat(messageDeliveryPort.lastCommand.recipient()).isEqualTo("override@example.test");
        }

        private void arrangeReplay(
                        AuthFlowSessionSnapshot envelope,
                        UsernameRecoveryWorkflowState sidecar) {
                envelopes.snapshot = envelope;
                workflows.state = sidecar;
                idempotencyPort.reserveOutcome = UsernameRecoveryIdempotencyOutcome.COMPLETED_REPLAY;
                idempotencyPort.storedRecord = completedRecord(
                                UsernameRecoveryRequestReplayCodec.encode(new AuthFlowSessionId(SESSION_ID)));
        }

        private AuthFlowSessionSnapshot replayEnvelope(
                        String id,
                        AuthFlowScenario scenario,
                        AuthFlowStatus status,
                        Instant expiresAt) {
                return new AuthFlowSessionSnapshot(
                                new AuthFlowSession(
                                                new AuthFlowSessionId(id), scenario, status,
                                                new Sha256CsrfTokenHasher().hash(CSRF), null,
                                                AuthFlowSessionData.empty(), NOW.minusSeconds(10),
                                                NOW.minusSeconds(10), expiresAt),
                                new SessionRevision("0123456789abcdef0123456789abcdef"));
        }

        private UsernameRecoveryWorkflowState replaySidecar(
                        String id,
                        UsernameRecoveryDeliveryState deliveryState,
                        UsernameRecoveryWorkflowStatus status) {
                boolean requested = deliveryState == UsernameRecoveryDeliveryState.REQUESTED;
                UsernameRecoverySessionCandidate target = new UsernameRecoverySessionCandidate(
                                new UsernameRecoveryCandidate(
                                                new UsernameRecoverySchemeRef(REGISTERED_REF), "DB", "DBA", true,
                                                "user-1", deliveryState),
                                requested ? NOW : null,
                                requested ? "op_sender_1" : null);
                UsernameRecoverySessionCandidate available = new UsernameRecoverySessionCandidate(
                                new UsernameRecoveryCandidate(
                                                new UsernameRecoverySchemeRef("sch_opaque-3"), "DB", "DBC", true,
                                                "user-2", UsernameRecoveryDeliveryState.AVAILABLE),
                                null, null);
                return new UsernameRecoveryWorkflowState(
                                new AuthFlowSessionId(id), status,
                                new UsernameRecoveryWorkflowStateRevision(0),
                                new UsernameRecoveryVerifiedDestination(
                                                "pepperc@ah.ozk",
                                                UsernameRecoveryVerifiedDestination.Channel.EMAIL),
                                Locale.ENGLISH, List.of(target, available));
        }

        private void assertReplaySystemFailureWithoutSideEffects() {
                StepVerifier.create(service.request(command(), CSRF, null, null))
                                .expectErrorSatisfies(error -> {
                                        assertThat(error).isInstanceOf(UsernameRecoveryRequestException.class);
                                        assertThat(((UsernameRecoveryRequestException) error).reason())
                                                        .isEqualTo(Reason.SYSTEM_FAILURE);
                                })
                                .verify();
                assertThat(messageDeliveryPort.deliverCalls).isZero();
                assertThat(workflows.updates).isZero();
                assertThat(idempotencyPort.completeCalls).isZero();
                assertThat(idempotencyPort.unknownOutcomeCalls).isZero();
        }

        @Test
        void replayWithEnvelopeIdentityMismatchFailsClosedWithoutSideEffects() {
                arrangeReplay(
                                replayEnvelope("afs_other", AuthFlowScenario.USERNAME_RECOVERY,
                                                AuthFlowStatus.STARTED, NOW.plusSeconds(TTL_SECONDS)),
                                replaySidecar(SESSION_ID, UsernameRecoveryDeliveryState.REQUESTED,
                                                UsernameRecoveryWorkflowStatus.PARTIALLY_REQUESTED));
                assertReplaySystemFailureWithoutSideEffects();
        }

        @Test
        void replayWithWrongEnvelopeScenarioFailsClosedWithoutSideEffects() {
                arrangeReplay(
                                replayEnvelope(SESSION_ID, AuthFlowScenario.LOGIN,
                                                AuthFlowStatus.STARTED, NOW.plusSeconds(TTL_SECONDS)),
                                replaySidecar(SESSION_ID, UsernameRecoveryDeliveryState.REQUESTED,
                                                UsernameRecoveryWorkflowStatus.PARTIALLY_REQUESTED));
                assertReplaySystemFailureWithoutSideEffects();
        }

        @Test
        void replayWithExpiredEnvelopeFailsClosedWithoutSideEffects() {
                arrangeReplay(
                                replayEnvelope(SESSION_ID, AuthFlowScenario.USERNAME_RECOVERY,
                                                AuthFlowStatus.STARTED, NOW.minusSeconds(1)),
                                replaySidecar(SESSION_ID, UsernameRecoveryDeliveryState.REQUESTED,
                                                UsernameRecoveryWorkflowStatus.PARTIALLY_REQUESTED));
                assertReplaySystemFailureWithoutSideEffects();
        }

        @Test
        void replayWithTerminalEnvelopeFailsClosedWithoutSideEffects() {
                arrangeReplay(
                                replayEnvelope(SESSION_ID, AuthFlowScenario.USERNAME_RECOVERY,
                                                AuthFlowStatus.COMPLETED, NOW.plusSeconds(TTL_SECONDS)),
                                replaySidecar(SESSION_ID, UsernameRecoveryDeliveryState.REQUESTED,
                                                UsernameRecoveryWorkflowStatus.PARTIALLY_REQUESTED));
                assertReplaySystemFailureWithoutSideEffects();
        }

        @Test
        void replayWithMissingSidecarFailsClosedWithoutSideEffects() {
                arrangeReplay(
                                replayEnvelope(SESSION_ID, AuthFlowScenario.USERNAME_RECOVERY,
                                                AuthFlowStatus.STARTED, NOW.plusSeconds(TTL_SECONDS)),
                                null);
                assertReplaySystemFailureWithoutSideEffects();
        }

        @Test
        void replayWithSidecarIdentityMismatchFailsClosedWithoutSideEffects() {
                arrangeReplay(
                                replayEnvelope(SESSION_ID, AuthFlowScenario.USERNAME_RECOVERY,
                                                AuthFlowStatus.STARTED, NOW.plusSeconds(TTL_SECONDS)),
                                replaySidecar("afs_other", UsernameRecoveryDeliveryState.REQUESTED,
                                                UsernameRecoveryWorkflowStatus.PARTIALLY_REQUESTED));
                assertReplaySystemFailureWithoutSideEffects();
        }

        @Test
        void completedReplayRebuildsUnknownOutcomeWithoutResendingOrMutation() {
                arrangeReplay(
                                replayEnvelope(SESSION_ID, AuthFlowScenario.USERNAME_RECOVERY,
                                                AuthFlowStatus.STARTED, NOW.plusSeconds(TTL_SECONDS)),
                                replaySidecar(SESSION_ID, UsernameRecoveryDeliveryState.UNKNOWN_OUTCOME,
                                                UsernameRecoveryWorkflowStatus.ACTIVE));
                StepVerifier.create(service.request(command(), CSRF, null, null))
                                .assertNext(result -> assertThat(result.outcome())
                                                .isEqualTo(UsernameRecoveryRequestResult.Outcome.UNKNOWN_OUTCOME))
                                .verifyComplete();
                assertThat(messageDeliveryPort.deliverCalls).isZero();
                assertThat(workflows.updates).isZero();
                assertThat(idempotencyPort.completeCalls).isZero();
        }

        @Test
        void completedReplayRebuildsAcceptedProjectionWithoutResending() {
                arrangeReplay(
                                replayEnvelope(SESSION_ID, AuthFlowScenario.USERNAME_RECOVERY,
                                                AuthFlowStatus.STARTED, NOW.plusSeconds(TTL_SECONDS)),
                                replaySidecar(SESSION_ID, UsernameRecoveryDeliveryState.REQUESTED,
                                                UsernameRecoveryWorkflowStatus.PARTIALLY_REQUESTED));

                StepVerifier.create(service.request(command(), CSRF, null, null))
                                .assertNext(result -> {
                                        assertThat(result.outcome()).isEqualTo(
                                                        UsernameRecoveryRequestResult.Outcome.REQUEST_ACCEPTED);
                                        assertThat(result.workflowStatus())
                                                        .isEqualTo(UsernameRecoveryWorkflowStatus.PARTIALLY_REQUESTED);
                                        assertThat(result.schemeRef())
                                                        .isEqualTo(new UsernameRecoverySchemeRef(REGISTERED_REF));
                                        assertThat(result.maskedDestination()).isEqualTo("p****c@ah.ozk");
                                        assertThat(result.destinationType())
                                                        .isEqualTo(UsernameRecoveryDestinationType.EMAIL);
                                })
                                .verifyComplete();

                assertThat(messageDeliveryPort.deliverCalls).isZero();
                assertThat(idempotencyPort.completeCalls).isZero();
        }

        @Test
        void completedReplayMasksOriginalDestinationWithoutConsultingCurrentOverride() {
                effectiveDestinationPort.result = new UsernameRecoveryEffectiveDestinationResult(
                                "changed-override@example.test",
                                UsernameRecoveryVerifiedDestination.Channel.EMAIL);
                effectiveDestinationPort.fails = true;
                arrangeReplay(
                                replayEnvelope(SESSION_ID, AuthFlowScenario.USERNAME_RECOVERY,
                                                AuthFlowStatus.STARTED, NOW.plusSeconds(TTL_SECONDS)),
                                replaySidecar(SESSION_ID, UsernameRecoveryDeliveryState.REQUESTED,
                                                UsernameRecoveryWorkflowStatus.PARTIALLY_REQUESTED));

                StepVerifier.create(service.request(command(), CSRF, null, null))
                                .assertNext(result -> {
                                        assertThat(result.outcome()).isEqualTo(
                                                        UsernameRecoveryRequestResult.Outcome.REQUEST_ACCEPTED);
                                        assertThat(result.maskedDestination()).isEqualTo("p****c@ah.ozk");
                                        assertThat(result.destinationType())
                                                        .isEqualTo(UsernameRecoveryDestinationType.EMAIL);
                                })
                                .verifyComplete();

                assertThat(messageDeliveryPort.deliverCalls).isZero();
                assertThat(effectiveDestinationPort.resolveCalls).isZero();
        }

        @Test
        void completedReplayForFailedCandidateRebuildsSystemFailedWithoutResend() {
                arrangeReplay(
                                replayEnvelope(SESSION_ID, AuthFlowScenario.USERNAME_RECOVERY,
                                                AuthFlowStatus.STARTED, NOW.plusSeconds(TTL_SECONDS)),
                                replaySidecar(SESSION_ID, UsernameRecoveryDeliveryState.FAILED,
                                                UsernameRecoveryWorkflowStatus.ACTIVE));

                StepVerifier.create(service.request(command(), CSRF, null, null))
                                .assertNext(result -> assertThat(result.outcome())
                                                .isEqualTo(UsernameRecoveryRequestResult.Outcome.SYSTEM_FAILED))
                                .verifyComplete();

                assertThat(messageDeliveryPort.deliverCalls).isZero();
        }

        @Test
        void malformedReplaySnapshotFailsClosedAsSystemFailureWithoutResend() {
                idempotencyPort.reserveOutcome = UsernameRecoveryIdempotencyOutcome.COMPLETED_REPLAY;
                idempotencyPort.storedRecord = completedRecord(
                                new UsernameRecoveryIdempotencyRecord.ReplaySnapshot("corrupt-payload"));

                StepVerifier.create(service.request(command(), CSRF, null, null))
                                .expectErrorSatisfies(error -> {
                                        assertThat(error).isInstanceOf(UsernameRecoveryRequestException.class);
                                        assertThat(((UsernameRecoveryRequestException) error).reason())
                                                        .isEqualTo(Reason.SYSTEM_FAILURE);
                                })
                                .verify();

                assertThat(messageDeliveryPort.deliverCalls).isZero();
        }

        @Test
        void replayWithMissingRecordFailsClosedAsSystemFailure() {
                idempotencyPort.reserveOutcome = UsernameRecoveryIdempotencyOutcome.COMPLETED_REPLAY;
                idempotencyPort.storedRecord = null;

                StepVerifier.create(service.request(command(), CSRF, null, null))
                                .expectErrorSatisfies(error -> {
                                        assertThat(error).isInstanceOf(UsernameRecoveryRequestException.class);
                                        assertThat(((UsernameRecoveryRequestException) error).reason())
                                                        .isEqualTo(Reason.SYSTEM_FAILURE);
                                })
                                .verify();

                assertThat(messageDeliveryPort.deliverCalls).isZero();
        }

        @Test
        void replayWithAbsentSessionFailsClosedAsSystemFailureWithoutBlindCreation() {
                arrangeReplay(null,
                                replaySidecar(SESSION_ID, UsernameRecoveryDeliveryState.REQUESTED,
                                                UsernameRecoveryWorkflowStatus.PARTIALLY_REQUESTED));

                StepVerifier.create(service.request(command(), CSRF, null, null))
                                .expectErrorSatisfies(error -> {
                                        assertThat(error).isInstanceOf(UsernameRecoveryRequestException.class);
                                        assertThat(((UsernameRecoveryRequestException) error).reason())
                                                        .isEqualTo(Reason.SYSTEM_FAILURE);
                                })
                                .verify();

                assertThat(messageDeliveryPort.deliverCalls).isZero();
        }

        @Test
        void replayWithUnconclusiveCandidateFailsClosedAsSystemFailure() {
                UsernameRecoveryWorkflowState stored = sessionWithCandidateState(
                                UsernameRecoveryDeliveryState.RESERVED, UsernameRecoveryWorkflowStatus.ACTIVE);
                storeScenario(stored);
                idempotencyPort.reserveOutcome = UsernameRecoveryIdempotencyOutcome.COMPLETED_REPLAY;
                idempotencyPort.storedRecord = completedRecord(
                                UsernameRecoveryRequestReplayCodec.encode(stored.authFlowSessionId()));

                StepVerifier.create(service.request(command(), CSRF, null, null))
                                .expectErrorSatisfies(error -> {
                                        assertThat(error).isInstanceOf(UsernameRecoveryRequestException.class);
                                        assertThat(((UsernameRecoveryRequestException) error).reason())
                                                        .isEqualTo(Reason.SYSTEM_FAILURE);
                                })
                                .verify();

                assertThat(messageDeliveryPort.deliverCalls).isZero();
        }

        @Test
        void differentKeyAfterRequestedReturnsAlreadyRequestedWithoutResending() {
                UsernameRecoveryWorkflowState stored = sessionWithCandidateState(
                                UsernameRecoveryDeliveryState.REQUESTED,
                                UsernameRecoveryWorkflowStatus.PARTIALLY_REQUESTED);
                storeScenario(stored);
                UsernameRecoveryRequestCommand otherKey = new UsernameRecoveryRequestCommand(
                                new AuthFlowSessionId(SESSION_ID),
                                new UsernameRecoverySchemeRef(REGISTERED_REF),
                                "another_key_1234");

                StepVerifier.create(service.request(otherKey, CSRF, null, null))
                                .assertNext(result -> assertThat(result.outcome())
                                                .isEqualTo(UsernameRecoveryRequestResult.Outcome.ALREADY_REQUESTED))
                                .verifyComplete();

                assertThat(messageDeliveryPort.deliverCalls).isZero();
        }

        private static UsernameRecoveryRequestCommand command() {
                return new UsernameRecoveryRequestCommand(
                                new AuthFlowSessionId(SESSION_ID),
                                new UsernameRecoverySchemeRef(REGISTERED_REF),
                                IDEM_KEY);
        }

        private static UsernameRecoveryWorkflowState activeWorkflowState() {
                return workflowState(
                                UsernameRecoveryWorkflowStatus.ACTIVE,
                                List.of(candidate(REGISTERED_REF, "DBA", true, "user-1",
                                                UsernameRecoveryDeliveryState.AVAILABLE),
                                                candidate(UNREGISTERED_REF, "DBB", false, null,
                                                                UsernameRecoveryDeliveryState.AVAILABLE)));
        }
        private static UsernameRecoveryWorkflowState singleRegisteredSession() {
                return workflowState(
                                UsernameRecoveryWorkflowStatus.ACTIVE,
                                List.of(candidate(REGISTERED_REF, "DBA", true, "user-1",
                                                UsernameRecoveryDeliveryState.AVAILABLE)));
        }
        private static UsernameRecoveryWorkflowState twoRegisteredSession() {
                return workflowState(
                                UsernameRecoveryWorkflowStatus.ACTIVE,
                                List.of(candidate(REGISTERED_REF, "DBA", true, "user-1",
                                                UsernameRecoveryDeliveryState.AVAILABLE),
                                                candidate("sch_opaque-3", "DBC", true, "user-2",
                                                                UsernameRecoveryDeliveryState.AVAILABLE)));
        }
        private static UsernameRecoveryWorkflowState sessionWithCandidateState(
                        UsernameRecoveryDeliveryState state, UsernameRecoveryWorkflowStatus status) {
                boolean secondRequested = status == UsernameRecoveryWorkflowStatus.COMPLETED;
                return workflowState(
                                status,
                                List.of(candidate(REGISTERED_REF, "DBA", true, "user-1", state),
                                                candidate("sch_opaque-3", "DBC", true, "user-2",
                                                                secondRequested
                                                                                ? UsernameRecoveryDeliveryState.REQUESTED
                                                                                : UsernameRecoveryDeliveryState.AVAILABLE)));
        }
        private static UsernameRecoverySessionCandidate candidate(
                        String schemeRef, String trustCode, boolean registered, String userName,
                        UsernameRecoveryDeliveryState state) {
                boolean requested = state == UsernameRecoveryDeliveryState.REQUESTED;
                return new UsernameRecoverySessionCandidate(
                                new UsernameRecoveryCandidate(
                                                new UsernameRecoverySchemeRef(schemeRef), "DB", trustCode,
                                                registered, userName, state),
                                requested ? NOW : null,
                                requested ? "op_sender_" + trustCode : null);
        }
        private static UsernameRecoveryWorkflowState workflowState(
                        UsernameRecoveryWorkflowStatus status,
                        List<UsernameRecoverySessionCandidate> candidates) {
                return new UsernameRecoveryWorkflowState(
                                new AuthFlowSessionId(SESSION_ID), status,
                                new UsernameRecoveryWorkflowStateRevision(0),
                                new UsernameRecoveryVerifiedDestination(
                                                "pepperc@ah.ozk",
                                                UsernameRecoveryVerifiedDestination.Channel.EMAIL),
                                Locale.ENGLISH, candidates);
        }
        private static UsernameRecoveryIdempotencyRecord completedRecord(
                        UsernameRecoveryIdempotencyRecord.ReplaySnapshot snapshot) {
                return new UsernameRecoveryIdempotencyRecord(
                                UsernameRecoveryIdempotencyAction.REQUEST,
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

        private void storeScenario(UsernameRecoveryWorkflowState state) {
                workflows.state = state;
                envelopes.snapshot = state == null ? null : replayEnvelope(
                                state.authFlowSessionId().value(),
                                AuthFlowScenario.USERNAME_RECOVERY,
                                state.status() == UsernameRecoveryWorkflowStatus.COMPLETED
                                                ? AuthFlowStatus.COMPLETED
                                                : AuthFlowStatus.STARTED,
                                NOW.plusSeconds(TTL_SECONDS));
        }
        private static final class FakeIdempotencyPort implements UsernameRecoveryIdempotencyPort {

                private final List<String> callLog;
                private UsernameRecoveryIdempotencyOutcome reserveOutcome = UsernameRecoveryIdempotencyOutcome.RESERVED;
                private boolean reserveFails;
                private boolean completeFails;
                private boolean unknownOutcomeFails;
                private UsernameRecoveryIdempotencyRecord storedRecord;
                private UsernameRecoveryIdempotencyPort.CompleteCommand lastCompleted;
                private int completeCalls;
                private int unknownOutcomeCalls;

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
                                return Mono.just(new ReservationResult(reserveOutcome,
                                                new UsernameRecoveryIdempotencyReservation(
                                                                command.action(), command.idempotencyKey(),
                                                                command.fingerprint(),
                                                                command.operationReference(), command.expiresAt())));
                        }
                        return Mono.just(new ReservationResult(reserveOutcome, null));
                }

                @Override
                public Mono<Optional<UsernameRecoveryIdempotencyRecord>> resolve(
                                UsernameRecoveryIdempotencyAction action,
                                UsernameRecoveryIdempotencyKey idempotencyKey) {
                        callLog.add("idem.resolve");
                        if (storedRecord == null) {
                                return Mono.just(Optional.empty());
                        }
                        return Mono.just(Optional.of(storedRecord));
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
                                        command.action(), command.idempotencyKey(), command.fingerprint(),
                                        command.operationReference(),
                                        UsernameRecoveryIdempotencyRecord.Status.COMPLETED,
                                        command.replaySnapshot(), command.now(), command.now(),
                                        command.now().plus(command.ttl())));
                }

                @Override
                public Mono<UsernameRecoveryIdempotencyRecord> recordUnknownOutcome(UnknownOutcomeCommand command) {
                        callLog.add("idem.unknown");
                        unknownOutcomeCalls++;
                        if (unknownOutcomeFails) {
                                return Mono.error(new IllegalStateException("unknown outcome infrastructure failure"));
                        }
                        return Mono.just(new UsernameRecoveryIdempotencyRecord(
                                        command.action(), command.idempotencyKey(), command.fingerprint(),
                                        command.operationReference(),
                                        UsernameRecoveryIdempotencyRecord.Status.UNKNOWN_OUTCOME,
                                        null, command.now(), command.now(), command.now().plus(command.ttl())));
                }
        }

        private static final class FakeEffectiveDestinationPort
                        implements UsernameRecoveryEffectiveDestinationPort {

                private UsernameRecoveryEffectiveDestinationResult result = new UsernameRecoveryEffectiveDestinationResult(
                                "pepperc@ah.ozk", UsernameRecoveryVerifiedDestination.Channel.EMAIL);
                private boolean fails;
                private int resolveCalls;

                @Override
                public Mono<UsernameRecoveryEffectiveDestinationResult> resolve(
                                UsernameRecoveryEffectiveDestinationCommand command) {
                        resolveCalls++;
                        if (fails) {
                                return Mono.error(
                                                new IllegalStateException("effective destination resolution failure"));
                        }
                        return Mono.just(result);
                }
        }

        private static final class FakeMessageDeliveryPort implements MessageDeliveryPort {

                private MessageDeliveryResult result = new MessageDeliveryResult(
                                MessageDeliveryStatus.ACCEPTED, MessageDeliveryChannel.EMAIL, "provider-msg-1", null,
                                null);
                private MessageDeliveryCommand lastCommand;
                private boolean fails;
                private int deliverCalls;

                @Override
                public Mono<MessageDeliveryResult> deliver(MessageDeliveryCommand command) {
                        deliverCalls++;
                        lastCommand = command;
                        if (fails) {
                                return Mono.error(new IllegalStateException("delivery infrastructure failure"));
                        }
                        return Mono.just(result);
                }
        }

        private final class FakeAuthFlowPort implements AuthFlowSessionPort {
                private AuthFlowSessionSnapshot snapshot;
                private boolean fail;
                private int loads;
                private int completeCalls;
                private com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionMutationOutcome completeOutcome =
                                com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionMutationOutcome.APPLIED;

                @Override
                public Mono<Optional<AuthFlowSessionSnapshot>> loadSnapshot(AuthFlowSessionId id) {
                        loads++;
                        if (fail) {
                                return Mono.error(new IllegalStateException("load failed"));
                        }
                        return Mono.just(Optional.ofNullable(snapshot));
                }

                @Override
                public Mono<com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionMutationResult>
                                completeRevisioned(AuthFlowSessionSnapshot expected, Instant now) {
                        completeCalls++;
                        if (completeOutcome
                                        != com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionMutationOutcome.APPLIED) {
                                return Mono.just(com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionMutationResult.outcome(
                                                completeOutcome));
                        }
                        AuthFlowSession current = expected.session();
                        AuthFlowSession completed = new AuthFlowSession(
                                        current.authFlowSessionId(), current.scenario(), AuthFlowStatus.COMPLETED,
                                        current.csrfTokenHash(), current.activeOtpChallengeId(), current.data(),
                                        current.createdAt(), now, current.expiresAt());
                        snapshot = new AuthFlowSessionSnapshot(completed,
                                        new SessionRevision("11111111111111111111111111111111"));
                        return Mono.just(new com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionMutationResult(
                                        com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionMutationOutcome.APPLIED,
                                        snapshot));
                }

                @Override
                public Mono<AuthFlowSession> create(
                                com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionCreateCommand c) {
                        return Mono.error(new UnsupportedOperationException());
                }

                @Override
                public Mono<Optional<AuthFlowSession>> load(AuthFlowSessionId id) {
                        return Mono.just(Optional.ofNullable(snapshot).map(AuthFlowSessionSnapshot::session));
                }
        }

        private final class FakeWorkflowPort implements UsernameRecoveryWorkflowStatePort {
                private UsernameRecoveryWorkflowState state;
                private UsernameRecoveryWorkflowStateMutationOutcome updateOutcome = UsernameRecoveryWorkflowStateMutationOutcome.APPLIED;
                private boolean loadFail;
                private boolean updateFail;
                private int forceUpdateOutcomeAtCall = -1;
                private UsernameRecoveryWorkflowStateMutationOutcome forcedUpdateOutcome;
                private int loads;
                private int updates;
                private UsernameRecoveryWorkflowStateMutationCommand lastMutation;

                @Override
                public Mono<UsernameRecoveryWorkflowStateLoadResult> load(AuthFlowSessionId id) {
                        loads++;
                        if (loadFail)
                                return Mono.error(new IllegalStateException("load failed"));
                        return Mono.just(state == null ? UsernameRecoveryWorkflowStateLoadResult.notFound()
                                        : UsernameRecoveryWorkflowStateLoadResult.loaded(state));
                }

                @Override
                public Mono<UsernameRecoveryWorkflowStateMutationResult> update(
                                UsernameRecoveryWorkflowStateMutationCommand command) {
                        updates++;
                        lastMutation = command;
                        if (updateFail)
                                return Mono.error(new IllegalStateException("update failed"));
                        if (updates == forceUpdateOutcomeAtCall) {
                                return Mono.just(UsernameRecoveryWorkflowStateMutationResult.outcome(
                                                forcedUpdateOutcome));
                        }
                        if (updateOutcome == UsernameRecoveryWorkflowStateMutationOutcome.APPLIED) {
                                state = command.updatedState();
                                return Mono.just(new UsernameRecoveryWorkflowStateMutationResult(updateOutcome, state));
                        }
                        return Mono.just(UsernameRecoveryWorkflowStateMutationResult.outcome(updateOutcome));
                }

                @Override
                public Mono<UsernameRecoveryWorkflowStateMutationResult> create(
                                com.bct.ngtpa.apiservice.application.port.out.UsernameRecoveryWorkflowStateCreateCommand c) {
                        return Mono.error(new UnsupportedOperationException());
                }

                @Override
                public Mono<UsernameRecoveryWorkflowStateDeleteResult> delete(AuthFlowSessionId id) {
                        return Mono.error(new UnsupportedOperationException());
                }
        }

}
