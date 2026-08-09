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
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionId;
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
import com.bct.ngtpa.apiservice.application.port.out.UsernameRecoverySessionDeleteResult;
import com.bct.ngtpa.apiservice.application.port.out.UsernameRecoverySessionLoadResult;
import com.bct.ngtpa.apiservice.application.port.out.UsernameRecoverySessionMutationCommand;
import com.bct.ngtpa.apiservice.application.port.out.UsernameRecoverySessionMutationOutcome;
import com.bct.ngtpa.apiservice.application.port.out.UsernameRecoverySessionMutationResult;
import com.bct.ngtpa.apiservice.application.port.out.UsernameRecoverySessionPort;
import com.bct.ngtpa.apiservice.application.usecase.auth.usernamerecovery.UsernameRecoveryRequestException.Reason;
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
 * Verifies the Task 020 {@link RequestUsernameRecoveryService} orchestration: validation, semantic
 * fingerprinting, REQUEST idempotency classification, origin/referer enforcement, candidate
 * reservation mapping, effective-destination resolution, at-most-once localized sender invocation,
 * accepted/definite-failure/unknown delivery handling, post-acceptance persistence safety, and
 * completed replay without resending.
 *
 * <p>Design references: {@code .cline/tasks/020-request-use-case.md} and
 * {@code docs/design/6-username-recovery/username-recovery-api-contract-design.md} section 8
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
    private final FakeSessionPort sessionPort = new FakeSessionPort(callLog);
    private final FakeEffectiveDestinationPort effectiveDestinationPort =
            new FakeEffectiveDestinationPort();
    private final FakeMessageDeliveryPort messageDeliveryPort = new FakeMessageDeliveryPort();

    private UsernameRecoveryReservationService reservationService;
    private RequestUsernameRecoveryService service;

    @BeforeEach
    void setUp() {
        sessionPort.store(activeSession());
        reservationService = new UsernameRecoveryReservationService(
                sessionPort, new CsrfTokenValidationService(new Sha256CsrfTokenHasher()));
        service = newService(List.of());
    }

    private RequestUsernameRecoveryService newService(List<String> allowedOrigins) {
        return new RequestUsernameRecoveryService(
                new UsernameRecoveryRequestFingerprintGenerator(),
                idempotencyPort,
                reservationService,
                effectiveDestinationPort,
                sessionPort,
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
                .assertNext(result ->
                        assertThat(result.outcome()).isEqualTo(UsernameRecoveryRequestResult.Outcome.REQUEST_ACCEPTED))
                .verifyComplete();

        assertThat(messageDeliveryPort.deliverCalls).isEqualTo(1);
    }

    @Test
    void sessionAbsentMapsToTypedSessionAbsentWithoutDelivery() {
        sessionPort.stored = null;

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
        UsernameRecoverySession stored = activeSession();
        sessionPort.store(new UsernameRecoverySession(
                stored.sessionId(), stored.scenario(), stored.status(), stored.revision(),
                stored.csrfHash(), stored.destination(), stored.language(),
                NOW.minusSeconds(10), NOW.minusSeconds(1), stored.candidates()));

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
                .assertNext(result ->
                        assertThat(result.outcome()).isEqualTo(UsernameRecoveryRequestResult.Outcome.INVALID_SCHEME_REF))
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
                .assertNext(result ->
                        assertThat(result.outcome())
                                .isEqualTo(UsernameRecoveryRequestResult.Outcome.UNREGISTERED_CANDIDATE))
                .verifyComplete();

        assertThat(messageDeliveryPort.deliverCalls).isZero();
    }

    @Test
    void alreadyRequestedCandidateReturnsTypedResultWithoutResending() {
        sessionPort.store(sessionWithCandidateState(
                UsernameRecoveryDeliveryState.REQUESTED, UsernameRecoveryWorkflowStatus.PARTIALLY_REQUESTED));

        StepVerifier.create(service.request(command(), CSRF, null, null))
                .assertNext(result ->
                        assertThat(result.outcome())
                                .isEqualTo(UsernameRecoveryRequestResult.Outcome.ALREADY_REQUESTED))
                .verifyComplete();

        assertThat(messageDeliveryPort.deliverCalls).isZero();
    }
    @Test
    void completedWorkflowReturnsTypedCompletedResultWithoutMutation() {
        sessionPort.store(sessionWithCandidateState(
                UsernameRecoveryDeliveryState.REQUESTED, UsernameRecoveryWorkflowStatus.COMPLETED));

        StepVerifier.create(service.request(command(), CSRF, null, null))
                .assertNext(result ->
                        assertThat(result.outcome())
                                .isEqualTo(UsernameRecoveryRequestResult.Outcome.WORKFLOW_COMPLETED))
                .verifyComplete();

        assertThat(messageDeliveryPort.deliverCalls).isZero();
        assertThat(sessionPort.updateCalls).isZero();
    }

    @Test
    void reservationRevisionConflictMapsToTypedSignalWithoutDelivery() {
        sessionPort.forceUpdateOutcomeAtCall = 1;
        sessionPort.forcedUpdateOutcome = UsernameRecoverySessionMutationOutcome.REVISION_CONFLICT;

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
        sessionPort.store(sessionWithCandidateState(
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
        sessionPort.store(twoRegisteredSession());

        StepVerifier.create(service.request(command(), CSRF, null, null))
                .assertNext(result -> {
                    assertThat(result.outcome()).isEqualTo(UsernameRecoveryRequestResult.Outcome.REQUEST_ACCEPTED);
                    assertThat(result.workflowStatus())
                            .isEqualTo(UsernameRecoveryWorkflowStatus.PARTIALLY_REQUESTED);
                    assertThat(result.schemeRef()).isEqualTo(new UsernameRecoverySchemeRef(REGISTERED_REF));
                    assertThat(result.maskedDestination()).isEqualTo("p****c@ah.ozk");
                    assertThat(result.destinationType()).isEqualTo(UsernameRecoveryDestinationType.EMAIL);
                })
                .verifyComplete();

        assertThat(messageDeliveryPort.deliverCalls).isEqualTo(1);
        assertThat(idempotencyPort.completeCalls).isEqualTo(1);
        assertThat(idempotencyPort.lastCompleted.replaySnapshot())
                .isEqualTo(UsernameRecoveryRequestReplayCodec.encode(
                        new UsernameRecoverySessionId(SESSION_ID)));

        UsernameRecoverySession stored = sessionPort.stored;
        UsernameRecoverySessionCandidate requested = stored.candidates().stream()
                .filter(c -> c.schemeRef().value().equals(REGISTERED_REF))
                .findFirst().orElseThrow();
        assertThat(requested.deliveryState()).isEqualTo(UsernameRecoveryDeliveryState.REQUESTED);
        assertThat(requested.requestedAt()).isEqualTo(NOW);
        assertThat(requested.senderReference()).isEqualTo("provider-msg-1");
        assertThat(stored.status()).isEqualTo(UsernameRecoveryWorkflowStatus.PARTIALLY_REQUESTED);
    }

    @Test
    void acceptedDeliveryCompletesWorkflowWhenAllRegisteredCandidatesAreRequested() {
        sessionPort.store(singleRegisteredSession());

        StepVerifier.create(service.request(command(), CSRF, null, null))
                .assertNext(result -> {
                    assertThat(result.outcome()).isEqualTo(UsernameRecoveryRequestResult.Outcome.REQUEST_ACCEPTED);
                    assertThat(result.workflowStatus()).isEqualTo(UsernameRecoveryWorkflowStatus.COMPLETED);
                })
                .verifyComplete();

        assertThat(sessionPort.stored.status()).isEqualTo(UsernameRecoveryWorkflowStatus.COMPLETED);
        assertThat(messageDeliveryPort.deliverCalls).isEqualTo(1);
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
                .assertNext(result ->
                        assertThat(result.outcome()).isEqualTo(UsernameRecoveryRequestResult.Outcome.REQUEST_ACCEPTED))
                .verifyComplete();

        UsernameRecoverySessionCandidate requested = sessionPort.stored.candidates().stream()
                .filter(c -> c.schemeRef().value().equals(REGISTERED_REF))
                .findFirst().orElseThrow();
        assertThat(requested.senderReference()).isNotBlank();
        assertThat(requested.senderReference()).startsWith("op_");
    }
    @Test
    void definiteDeliveryFailurePersistsFailedAndReturnsSystemFailedResult() {
        messageDeliveryPort.result = new MessageDeliveryResult(
                MessageDeliveryStatus.FAILED, MessageDeliveryChannel.EMAIL, null, "REJECTED", "rejected");

        StepVerifier.create(service.request(command(), CSRF, null, null))
                .assertNext(result ->
                        assertThat(result.outcome()).isEqualTo(UsernameRecoveryRequestResult.Outcome.SYSTEM_FAILED))
                .verifyComplete();

        UsernameRecoverySessionCandidate failed = sessionPort.stored.candidates().stream()
                .filter(c -> c.schemeRef().value().equals(REGISTERED_REF))
                .findFirst().orElseThrow();
        assertThat(failed.deliveryState()).isEqualTo(UsernameRecoveryDeliveryState.FAILED);
        assertThat(idempotencyPort.completeCalls).isEqualTo(1);
        assertThat(messageDeliveryPort.deliverCalls).isEqualTo(1);
    }

    @Test
    void unknownDeliveryOutcomePersistsStableUnknownAndReturnsUnknownResult() {
        messageDeliveryPort.result = new MessageDeliveryResult(
                MessageDeliveryStatus.UNKNOWN_OUTCOME, MessageDeliveryChannel.EMAIL, null, "UNKNOWN", "unknown");

        StepVerifier.create(service.request(command(), CSRF, null, null))
                .assertNext(result ->
                        assertThat(result.outcome()).isEqualTo(UsernameRecoveryRequestResult.Outcome.UNKNOWN_OUTCOME))
                .verifyComplete();

        UsernameRecoverySessionCandidate unknown = sessionPort.stored.candidates().stream()
                .filter(c -> c.schemeRef().value().equals(REGISTERED_REF))
                .findFirst().orElseThrow();
        assertThat(unknown.deliveryState()).isEqualTo(UsernameRecoveryDeliveryState.UNKNOWN_OUTCOME);
        assertThat(idempotencyPort.unknownOutcomeCalls).isEqualTo(1);
        assertThat(idempotencyPort.completeCalls).isZero();
        assertThat(messageDeliveryPort.deliverCalls).isEqualTo(1);
    }
    @Test
    void sessionCasConflictAfterAcceptanceRecordsStableUnknownOutcomeWithoutResend() {
        sessionPort.forceUpdateOutcomeAtCall = 2;
        sessionPort.forcedUpdateOutcome = UsernameRecoverySessionMutationOutcome.REVISION_CONFLICT;

        StepVerifier.create(service.request(command(), CSRF, null, null))
                .assertNext(result ->
                        assertThat(result.outcome()).isEqualTo(UsernameRecoveryRequestResult.Outcome.UNKNOWN_OUTCOME))
                .verifyComplete();

        assertThat(messageDeliveryPort.deliverCalls).isEqualTo(1);
        assertThat(idempotencyPort.unknownOutcomeCalls).isEqualTo(1);
        assertThat(idempotencyPort.completeCalls).isZero();
    }

    @Test
    void idempotencyCompleteFailureAfterAcceptanceRecordsStableUnknownOutcomeWithoutResend() {
        idempotencyPort.completeFails = true;

        StepVerifier.create(service.request(command(), CSRF, null, null))
                .assertNext(result ->
                        assertThat(result.outcome()).isEqualTo(UsernameRecoveryRequestResult.Outcome.UNKNOWN_OUTCOME))
                .verifyComplete();

        assertThat(messageDeliveryPort.deliverCalls).isEqualTo(1);
        assertThat(idempotencyPort.unknownOutcomeCalls).isEqualTo(1);
        assertThat(sessionPort.stored.candidates().stream()
                .filter(c -> c.schemeRef().value().equals(REGISTERED_REF))
                .findFirst().orElseThrow().deliveryState())
                .isEqualTo(UsernameRecoveryDeliveryState.REQUESTED);
    }

    @Test
    void unknownOutcomePersistenceFailureMapsToTypedSystemFailure() {
        idempotencyPort.unknownOutcomeFails = true;
        messageDeliveryPort.result = new MessageDeliveryResult(
                MessageDeliveryStatus.UNKNOWN_OUTCOME, MessageDeliveryChannel.EMAIL, null, "UNKNOWN", "unknown");

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
                .assertNext(result ->
                        assertThat(result.outcome()).isEqualTo(UsernameRecoveryRequestResult.Outcome.SYSTEM_FAILED))
                .verifyComplete();
        assertThat(messageDeliveryPort.deliverCalls).isZero();
        assertThat(idempotencyPort.completeCalls).isEqualTo(1);
        assertThat(sessionPort.stored.candidates().stream()
                .filter(c -> c.schemeRef().value().equals(REGISTERED_REF))
                .findFirst().orElseThrow().deliveryState())
                .isEqualTo(UsernameRecoveryDeliveryState.FAILED);
    }
    @Test
    void reactiveDeliveryFailurePersistsStableUnknownOutcomeWithoutResend() {
        messageDeliveryPort.fails = true;
        StepVerifier.create(service.request(command(), CSRF, null, null))
                .assertNext(result ->
                        assertThat(result.outcome()).isEqualTo(UsernameRecoveryRequestResult.Outcome.UNKNOWN_OUTCOME))
                .verifyComplete();
        assertThat(messageDeliveryPort.deliverCalls).isEqualTo(1);
        assertThat(idempotencyPort.unknownOutcomeCalls).isEqualTo(1);
        assertThat(idempotencyPort.completeCalls).isZero();
        assertThat(sessionPort.stored.candidates().stream()
                .filter(c -> c.schemeRef().value().equals(REGISTERED_REF))
                .findFirst().orElseThrow().deliveryState())
                .isEqualTo(UsernameRecoveryDeliveryState.UNKNOWN_OUTCOME);
    }

    @Test
    void nonProductionEffectiveOverrideIsMaskedAndUsedAsRecipient() {
        effectiveDestinationPort.result = new UsernameRecoveryEffectiveDestinationResult(
                "override@example.test", UsernameRecoveryVerifiedDestination.Channel.EMAIL);

        StepVerifier.create(service.request(command(), CSRF, null, null))
                .assertNext(result -> {
                    assertThat(result.outcome()).isEqualTo(UsernameRecoveryRequestResult.Outcome.REQUEST_ACCEPTED);
                    assertThat(result.maskedDestination()).isEqualTo("o****e@example.test");
                })
                .verifyComplete();

        assertThat(messageDeliveryPort.lastCommand.recipient()).isEqualTo("override@example.test");
        assertThat(sessionPort.stored.destination().value()).isEqualTo("pepperc@ah.ozk");
    }
    @Test
    void completedReplayRebuildsAcceptedProjectionWithoutResending() {
        UsernameRecoverySession stored = sessionWithCandidateState(
                UsernameRecoveryDeliveryState.REQUESTED, UsernameRecoveryWorkflowStatus.PARTIALLY_REQUESTED);
        sessionPort.store(stored);
        idempotencyPort.reserveOutcome = UsernameRecoveryIdempotencyOutcome.COMPLETED_REPLAY;
        idempotencyPort.storedRecord = completedRecord(
                UsernameRecoveryRequestReplayCodec.encode(stored.sessionId()));

        StepVerifier.create(service.request(command(), CSRF, null, null))
                .assertNext(result -> {
                    assertThat(result.outcome()).isEqualTo(UsernameRecoveryRequestResult.Outcome.REQUEST_ACCEPTED);
                    assertThat(result.workflowStatus())
                            .isEqualTo(UsernameRecoveryWorkflowStatus.PARTIALLY_REQUESTED);
                    assertThat(result.schemeRef()).isEqualTo(new UsernameRecoverySchemeRef(REGISTERED_REF));
                    assertThat(result.maskedDestination()).isEqualTo("p****c@ah.ozk");
                    assertThat(result.destinationType()).isEqualTo(UsernameRecoveryDestinationType.EMAIL);
                })
                .verifyComplete();

        assertThat(messageDeliveryPort.deliverCalls).isZero();
        assertThat(sessionPort.updateCalls).isZero();
        assertThat(idempotencyPort.completeCalls).isZero();
    }

    @Test
    void completedReplayForFailedCandidateRebuildsSystemFailedWithoutResend() {
        UsernameRecoverySession stored = sessionWithCandidateState(
                UsernameRecoveryDeliveryState.FAILED, UsernameRecoveryWorkflowStatus.ACTIVE);
        sessionPort.store(stored);
        idempotencyPort.reserveOutcome = UsernameRecoveryIdempotencyOutcome.COMPLETED_REPLAY;
        idempotencyPort.storedRecord = completedRecord(
                UsernameRecoveryRequestReplayCodec.encode(stored.sessionId()));

        StepVerifier.create(service.request(command(), CSRF, null, null))
                .assertNext(result ->
                        assertThat(result.outcome()).isEqualTo(UsernameRecoveryRequestResult.Outcome.SYSTEM_FAILED))
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
        UsernameRecoverySessionId sessionId = new UsernameRecoverySessionId("afs_gone");
        sessionPort.stored = null;
        idempotencyPort.reserveOutcome = UsernameRecoveryIdempotencyOutcome.COMPLETED_REPLAY;
        idempotencyPort.storedRecord = completedRecord(
                UsernameRecoveryRequestReplayCodec.encode(sessionId));

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
        UsernameRecoverySession stored = sessionWithCandidateState(
                UsernameRecoveryDeliveryState.RESERVED, UsernameRecoveryWorkflowStatus.ACTIVE);
        sessionPort.store(stored);
        idempotencyPort.reserveOutcome = UsernameRecoveryIdempotencyOutcome.COMPLETED_REPLAY;
        idempotencyPort.storedRecord = completedRecord(
                UsernameRecoveryRequestReplayCodec.encode(stored.sessionId()));

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
        UsernameRecoverySession stored = sessionWithCandidateState(
                UsernameRecoveryDeliveryState.REQUESTED, UsernameRecoveryWorkflowStatus.PARTIALLY_REQUESTED);
        sessionPort.store(stored);
        UsernameRecoveryRequestCommand otherKey = new UsernameRecoveryRequestCommand(
                new AuthFlowSessionId(SESSION_ID),
                new UsernameRecoverySchemeRef(REGISTERED_REF),
                "another_key_1234");

        StepVerifier.create(service.request(otherKey, CSRF, null, null))
                .assertNext(result ->
                        assertThat(result.outcome())
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

    private static UsernameRecoverySession activeSession() {
        UsernameRecoverySessionId sessionId = new UsernameRecoverySessionId(SESSION_ID);
        UsernameRecoverySchemeRef registeredRef = new UsernameRecoverySchemeRef(REGISTERED_REF);
        UsernameRecoverySchemeRef unregisteredRef = new UsernameRecoverySchemeRef(UNREGISTERED_REF);
        List<UsernameRecoverySessionCandidate> candidates = List.of(
                new UsernameRecoverySessionCandidate(
                        new UsernameRecoveryCandidate(registeredRef, "DB", "DBA", true, "user-1",
                                UsernameRecoveryDeliveryState.AVAILABLE), null, null),
                new UsernameRecoverySessionCandidate(
                        new UsernameRecoveryCandidate(unregisteredRef, "DB", "DBB", false, null,
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
    private static UsernameRecoverySession singleRegisteredSession() {
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

    private static UsernameRecoverySession twoRegisteredSession() {
        UsernameRecoverySessionId sessionId = new UsernameRecoverySessionId(SESSION_ID);
        UsernameRecoverySchemeRef firstRef = new UsernameRecoverySchemeRef(REGISTERED_REF);
        UsernameRecoverySchemeRef secondRef = new UsernameRecoverySchemeRef("sch_opaque-3");
        List<UsernameRecoverySessionCandidate> candidates = List.of(
                new UsernameRecoverySessionCandidate(
                        new UsernameRecoveryCandidate(firstRef, "DB", "DBA", true, "user-1",
                                UsernameRecoveryDeliveryState.AVAILABLE), null, null),
                new UsernameRecoverySessionCandidate(
                        new UsernameRecoveryCandidate(secondRef, "DB", "DBC", true, "user-2",
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

    private static UsernameRecoverySession sessionWithCandidateState(
            UsernameRecoveryDeliveryState state, UsernameRecoveryWorkflowStatus status) {
        UsernameRecoverySessionId sessionId = new UsernameRecoverySessionId(SESSION_ID);
        UsernameRecoverySchemeRef targetRef = new UsernameRecoverySchemeRef(REGISTERED_REF);
        UsernameRecoverySchemeRef secondRef = new UsernameRecoverySchemeRef("sch_opaque-3");
        boolean targetRequested = state == UsernameRecoveryDeliveryState.REQUESTED;
        boolean secondRequested = status == UsernameRecoveryWorkflowStatus.COMPLETED;
        List<UsernameRecoverySessionCandidate> candidates = List.of(
                new UsernameRecoverySessionCandidate(
                        new UsernameRecoveryCandidate(targetRef, "DB", "DBA", true, "user-1", state),
                        targetRequested ? NOW : null,
                        targetRequested ? "op_sender_1" : null),
                new UsernameRecoverySessionCandidate(
                        new UsernameRecoveryCandidate(secondRef, "DB", "DBC", true, "user-2",
                                secondRequested ? UsernameRecoveryDeliveryState.REQUESTED
                                        : UsernameRecoveryDeliveryState.AVAILABLE),
                        secondRequested ? NOW : null,
                        secondRequested ? "op_sender_2" : null));
        return new UsernameRecoverySession(
                sessionId,
                UsernameRecoverySession.Scenario.USERNAME_RECOVERY,
                status,
                new UsernameRecoverySessionRevision(0),
                new UsernameRecoveryCsrfHash(new Sha256CsrfTokenHasher().hash(CSRF)),
                new UsernameRecoveryVerifiedDestination("pepperc@ah.ozk",
                        UsernameRecoveryVerifiedDestination.Channel.EMAIL),
                Locale.ENGLISH,
                NOW,
                NOW.plusSeconds(TTL_SECONDS),
                candidates);
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
    private static final class FakeIdempotencyPort implements UsernameRecoveryIdempotencyPort {

        private final List<String> callLog;
        private UsernameRecoveryIdempotencyOutcome reserveOutcome =
                UsernameRecoveryIdempotencyOutcome.RESERVED;
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
                                command.action(), command.idempotencyKey(), command.fingerprint(),
                                command.operationReference(), command.expiresAt())));
            }
            return Mono.just(new ReservationResult(reserveOutcome, null));
        }

        @Override
        public Mono<Optional<UsernameRecoveryIdempotencyRecord>> resolve(
                UsernameRecoveryIdempotencyAction action, UsernameRecoveryIdempotencyKey idempotencyKey) {
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
                    command.operationReference(), UsernameRecoveryIdempotencyRecord.Status.COMPLETED,
                    command.replaySnapshot(), command.now(), command.now(), command.now().plus(command.ttl())));
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
                    command.operationReference(), UsernameRecoveryIdempotencyRecord.Status.UNKNOWN_OUTCOME,
                    null, command.now(), command.now(), command.now().plus(command.ttl())));
        }
    }
    private static final class FakeSessionPort implements UsernameRecoverySessionPort {

        private final List<String> callLog;
        private UsernameRecoverySession stored;
        private int forceUpdateOutcomeAtCall = -1;
        private UsernameRecoverySessionMutationOutcome forcedUpdateOutcome;
        private int updateCalls;
        private boolean loadFails;

        private FakeSessionPort(List<String> callLog) {
            this.callLog = callLog;
        }

        void store(UsernameRecoverySession session) {
            this.stored = session;
        }

        @Override
        public Mono<UsernameRecoverySessionMutationResult> create(UsernameRecoverySession session) {
            throw new UnsupportedOperationException("request flow never creates sessions");
        }

        @Override
        public Mono<UsernameRecoverySessionLoadResult> load(UsernameRecoverySessionId sessionId) {
            callLog.add("session.load");
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
            callLog.add("session.update");
            updateCalls++;
            if (updateCalls == forceUpdateOutcomeAtCall) {
                return Mono.just(UsernameRecoverySessionMutationResult.outcome(forcedUpdateOutcome));
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
            throw new UnsupportedOperationException("request flow never deletes sessions");
        }
    }
    private static final class FakeEffectiveDestinationPort
            implements UsernameRecoveryEffectiveDestinationPort {

        private UsernameRecoveryEffectiveDestinationResult result =
                new UsernameRecoveryEffectiveDestinationResult(
                        "pepperc@ah.ozk", UsernameRecoveryVerifiedDestination.Channel.EMAIL);
        private boolean fails;

        @Override
        public Mono<UsernameRecoveryEffectiveDestinationResult> resolve(
                UsernameRecoveryEffectiveDestinationCommand command) {
            if (fails) {
                return Mono.error(new IllegalStateException("effective destination resolution failure"));
            }
            return Mono.just(result);
        }
    }

    private static final class FakeMessageDeliveryPort implements MessageDeliveryPort {

        private MessageDeliveryResult result = new MessageDeliveryResult(
                MessageDeliveryStatus.ACCEPTED, MessageDeliveryChannel.EMAIL, "provider-msg-1", null, null);
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
}
