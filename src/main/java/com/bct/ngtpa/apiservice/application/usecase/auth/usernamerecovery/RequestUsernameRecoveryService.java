package com.bct.ngtpa.apiservice.application.usecase.auth.usernamerecovery;

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
import com.bct.ngtpa.apiservice.application.port.in.RequestUsernameRecoveryUseCase;
import com.bct.ngtpa.apiservice.application.port.out.MessageDeliveryPort;
import com.bct.ngtpa.apiservice.application.port.out.AuthFlowSessionPort;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowScenario;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionSnapshot;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowStatus;
import com.bct.ngtpa.apiservice.application.port.out.UsernameRecoveryEffectiveDestinationPort;
import com.bct.ngtpa.apiservice.application.port.out.UsernameRecoveryIdempotencyAction;
import com.bct.ngtpa.apiservice.application.port.out.UsernameRecoveryIdempotencyFingerprint;
import com.bct.ngtpa.apiservice.application.port.out.UsernameRecoveryIdempotencyKey;
import com.bct.ngtpa.apiservice.application.port.out.UsernameRecoveryIdempotencyOperationReference;
import com.bct.ngtpa.apiservice.application.port.out.UsernameRecoveryIdempotencyOutcome;
import com.bct.ngtpa.apiservice.application.port.out.UsernameRecoveryIdempotencyPort;
import com.bct.ngtpa.apiservice.application.port.out.UsernameRecoveryIdempotencyRecord;
import com.bct.ngtpa.apiservice.application.port.out.UsernameRecoveryIdempotencyRecord.ReplaySnapshot;
import com.bct.ngtpa.apiservice.application.port.out.UsernameRecoveryIdempotencyReservation;
import com.bct.ngtpa.apiservice.application.dto.session.usernamerecovery.UsernameRecoveryWorkflowState;
import com.bct.ngtpa.apiservice.application.port.out.UsernameRecoveryWorkflowStateLoadResult;
import com.bct.ngtpa.apiservice.application.port.out.UsernameRecoveryWorkflowStateMutationCommand;
import com.bct.ngtpa.apiservice.application.port.out.UsernameRecoveryWorkflowStateMutationOutcome;
import com.bct.ngtpa.apiservice.application.port.out.UsernameRecoveryWorkflowStatePort;
import com.bct.ngtpa.apiservice.application.service.security.csrf.OriginRefererValidator;
import com.bct.ngtpa.apiservice.domain.model.UsernameRecoveryDeliveryState;
import com.bct.ngtpa.apiservice.domain.model.UsernameRecoverySchemeRef;
import com.bct.ngtpa.apiservice.domain.model.UsernameRecoverySessionCandidate;
import com.bct.ngtpa.apiservice.domain.model.UsernameRecoveryVerifiedDestination;
import com.bct.ngtpa.apiservice.domain.model.UsernameRecoveryWorkflowStatus;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import reactor.core.publisher.Mono;

/**
 * Application orchestration for the Username Recovery request flow (Task 020).
 *
 * <p>
 * Design references: {@code .cline/tasks/020-request-use-case.md} canonical
 * orchestration order
 * (validate, fingerprint, reserve, classify, origin/referer check, candidate
 * reservation, effective
 * destination, at-most-once sender invocation, post-acceptance session CAS,
 * idempotency completion)
 * and
 * {@code docs/design/6-username-recovery/username-recovery-session-redis-design.md}
 * sections 4
 * and 8 (session-first persistence ordering, stable UNKNOWN_OUTCOME, no blind
 * resend).
 *
 * <p>
 * Security boundary: the committed inbound port
 * {@link RequestUsernameRecoveryUseCase} cannot
 * carry the raw CSRF token (018A decision). This service therefore exposes the
 * canonical richer
 * method
 * {@link #request(UsernameRecoveryRequestCommand, String, String, String)}
 * where the raw
 * CSRF and origin/referer enter at the method boundary and are forwarded to the
 * committed
 * reservation service / origin validator. The port method itself fails closed
 * because no raw CSRF
 * can enter through it.
 *
 * <p>
 * No-blind-resend guarantee: delivery occurs only after both the idempotency
 * reservation and the
 * candidate reservation CAS succeed; every post-delivery path either commits
 * the REQUESTED state
 * atomically or records a stable unknown outcome; the sender is never retried
 * automatically and the
 * session CAS is never retried automatically.
 */
public final class RequestUsernameRecoveryService implements RequestUsernameRecoveryUseCase {

        private final UsernameRecoveryRequestFingerprintGenerator fingerprintGenerator;
        private final UsernameRecoveryIdempotencyPort idempotencyPort;
        private final UsernameRecoveryReservationService reservationService;
        private final UsernameRecoveryEffectiveDestinationPort effectiveDestinationPort;
        private final AuthFlowSessionPort authFlowSessionPort;
        private final UsernameRecoveryWorkflowStatePort workflowStatePort;
        private final MessageDeliveryPort messageDeliveryPort;
        private final OriginRefererValidator originRefererValidator;
        private final List<String> allowedOrigins;
        private final UsernameRecoveryRequestDestinationMasker destinationMasker;
        private final Clock clock;
        private final int ttlSeconds;
        private final String emailSubject;
        private final String emailBody;
        private final String smsBody;
        private final SecureRandom secureRandom = new SecureRandom();

        public RequestUsernameRecoveryService(
                        UsernameRecoveryRequestFingerprintGenerator fingerprintGenerator,
                        UsernameRecoveryIdempotencyPort idempotencyPort,
                        UsernameRecoveryReservationService reservationService,
                        UsernameRecoveryEffectiveDestinationPort effectiveDestinationPort,
                        AuthFlowSessionPort authFlowSessionPort,
                        UsernameRecoveryWorkflowStatePort workflowStatePort,
                        MessageDeliveryPort messageDeliveryPort,
                        List<String> allowedOrigins,
                        Clock clock,
                        int ttlSeconds,
                        String emailSubject,
                        String emailBody,
                        String smsBody) {
                this.fingerprintGenerator = Objects.requireNonNull(fingerprintGenerator,
                                "fingerprintGenerator must not be null");
                this.idempotencyPort = Objects.requireNonNull(idempotencyPort, "idempotencyPort must not be null");
                this.reservationService = Objects.requireNonNull(reservationService,
                                "reservationService must not be null");
                this.effectiveDestinationPort = Objects.requireNonNull(
                                effectiveDestinationPort, "effectiveDestinationPort must not be null");
                this.authFlowSessionPort = Objects.requireNonNull(authFlowSessionPort, "authFlowSessionPort must not be null");
                this.workflowStatePort = Objects.requireNonNull(
                                workflowStatePort, "workflowStatePort must not be null");
                this.messageDeliveryPort = Objects.requireNonNull(messageDeliveryPort,
                                "messageDeliveryPort must not be null");
                this.originRefererValidator = new OriginRefererValidator();
                this.allowedOrigins = allowedOrigins == null ? List.of() : List.copyOf(allowedOrigins);
                this.destinationMasker = new UsernameRecoveryRequestDestinationMasker();
                this.clock = Objects.requireNonNull(clock, "clock must not be null");
                if (ttlSeconds <= 0) {
                        throw new IllegalArgumentException("ttlSeconds must be positive");
                }
                this.ttlSeconds = ttlSeconds;
                this.emailSubject = requireNonBlank(emailSubject, "emailSubject");
                this.emailBody = requireNonBlank(emailBody, "emailBody");
                this.smsBody = requireNonBlank(smsBody, "smsBody");
        }

        /**
         * Committed inbound-port shape. The port cannot carry the raw CSRF token (018A
         * decision), so
         * this method fails closed: delivery must never proceed without CSRF
         * validation.
         */
        @Override
        public Mono<UsernameRecoveryRequestResult> request(UsernameRecoveryRequestCommand command) {
                return Mono.error(UsernameRecoveryRequestException.securityContextRequired());
        }

        /**
         * Canonical orchestration entry where the raw CSRF token and origin/referer
         * enter at the method
         * boundary and are forwarded to the committed reservation service / origin
         * validator. They are
         * never stored in any record, command, field, log, exception, or
         * {@code toString()}.
         */
        public Mono<UsernameRecoveryRequestResult> request(
                        UsernameRecoveryRequestCommand command,
                        String rawCsrfToken,
                        String origin,
                        String referer) {
                if (command == null) {
                        return Mono.error(UsernameRecoveryRequestException.validationFailed());
                }
                UsernameRecoveryIdempotencyKey idempotencyKey;
                try {
                        idempotencyKey = new UsernameRecoveryIdempotencyKey(command.idempotencyKey());
                } catch (IllegalArgumentException exception) {
                        return Mono.error(UsernameRecoveryRequestException.validationFailed());
                }
                UsernameRecoveryIdempotencyFingerprint fingerprint = fingerprintGenerator.derive(command);
                Instant now = clock.instant();
                UsernameRecoveryIdempotencyOperationReference operationReference = generateOperationReference();
                Duration ttl = Duration.ofSeconds(ttlSeconds);
                UsernameRecoveryIdempotencyPort.ReserveCommand reserveCommand = new UsernameRecoveryIdempotencyPort.ReserveCommand(
                                UsernameRecoveryIdempotencyAction.REQUEST,
                                idempotencyKey,
                                fingerprint,
                                operationReference,
                                now,
                                ttl);
                return idempotencyPort.reserve(reserveCommand)
                                .onErrorMap(Exception.class, RequestPersistenceException::new)
                                .flatMap(reservation -> handleReservation(
                                                command,
                                                rawCsrfToken,
                                                origin,
                                                referer,
                                                idempotencyKey,
                                                fingerprint,
                                                now,
                                                ttl,
                                                reservation))
                                .onErrorResume(RequestPersistenceException.class,
                                                error -> Mono.error(
                                                                UsernameRecoveryRequestException.systemFailure(error)));
        }

        private Mono<UsernameRecoveryRequestResult> handleReservation(
                        UsernameRecoveryRequestCommand command,
                        String rawCsrfToken,
                        String origin,
                        String referer,
                        UsernameRecoveryIdempotencyKey idempotencyKey,
                        UsernameRecoveryIdempotencyFingerprint fingerprint,
                        Instant now,
                        Duration ttl,
                        UsernameRecoveryIdempotencyPort.ReservationResult reservation) {
                return switch (reservation.outcome()) {
                        case RESERVED -> proceed(command, rawCsrfToken, origin, referer, idempotencyKey,
                                        fingerprint, reservation.reservation(), now, ttl);
                        case COMPLETED_REPLAY -> replay(command, idempotencyKey, now);
                        case DUPLICATE_IN_PROGRESS ->
                                Mono.error(UsernameRecoveryRequestException.idempotencyInProgress());
                        case KEY_REUSED_WITH_DIFFERENT_REQUEST ->
                                Mono.error(UsernameRecoveryRequestException.keyReused());
                        case UNKNOWN_OUTCOME_EXISTING ->
                                Mono.error(UsernameRecoveryRequestException.unknownOutcomeExisting());
                        case SYSTEM_FAILURE -> Mono.error(UsernameRecoveryRequestException.systemFailure());
                        default -> throw new IllegalStateException(
                                        "unexpected idempotency reserve outcome: " + reservation.outcome());
                };
        }

        private Mono<UsernameRecoveryRequestResult> proceed(
                        UsernameRecoveryRequestCommand command,
                        String rawCsrfToken,
                        String origin,
                        String referer,
                        UsernameRecoveryIdempotencyKey idempotencyKey,
                        UsernameRecoveryIdempotencyFingerprint fingerprint,
                        UsernameRecoveryIdempotencyReservation reservation,
                        Instant now,
                        Duration ttl) {
                if (!originRefererAllowed(origin, referer)) {
                        return Mono.error(UsernameRecoveryRequestException.securityInvalid());
                }
                UsernameRecoveryReservationCommand reservationCommand = new UsernameRecoveryReservationCommand(
                                command.authFlowSessionId(), command.schemeRef(), now);
                return reservationService.reserve(reservationCommand, rawCsrfToken)
                                .onErrorMap(Exception.class, RequestPersistenceException::new)
                                .flatMap(result -> switch (result.outcome()) {
                                        case RESERVED ->
                                                deliver(command, idempotencyKey, fingerprint, reservation, now, ttl);
                                        case SESSION_ABSENT ->
                                                Mono.error(UsernameRecoveryRequestException.sessionAbsent());
                                        case SESSION_EXPIRED ->
                                                Mono.error(UsernameRecoveryRequestException.sessionExpired());
                                        case CSRF_INVALID ->
                                                Mono.error(UsernameRecoveryRequestException.securityInvalid());
                                        case SCHEME_REF_INVALID -> Mono.just(new UsernameRecoveryRequestResult(
                                                        UsernameRecoveryRequestResult.Outcome.INVALID_SCHEME_REF, null,
                                                        null, null, null));
                                        case REGISTRATION_REQUIRED -> Mono.just(new UsernameRecoveryRequestResult(
                                                        UsernameRecoveryRequestResult.Outcome.UNREGISTERED_CANDIDATE,
                                                        null, null, null, null));
                                        case ALREADY_REQUESTED -> Mono.just(new UsernameRecoveryRequestResult(
                                                        UsernameRecoveryRequestResult.Outcome.ALREADY_REQUESTED, null,
                                                        null, null, null));
                                        case CANDIDATE_NOT_AVAILABLE ->
                                                Mono.error(UsernameRecoveryRequestException.candidateNotAvailable());
                                        case WORKFLOW_TERMINAL -> Mono.just(new UsernameRecoveryRequestResult(
                                                        UsernameRecoveryRequestResult.Outcome.WORKFLOW_COMPLETED, null,
                                                        null, null, null));
                                        case REVISION_CONFLICT ->
                                                Mono.error(UsernameRecoveryRequestException.revisionConflict());
                                        case SYSTEM_FAILED ->
                                                Mono.error(UsernameRecoveryRequestException.systemFailure());
                                        default -> throw new IllegalStateException(
                                                        "unexpected reservation outcome: " + result.outcome());
                                });
        }

        private boolean originRefererAllowed(String origin, String referer) {
                return allowedOrigins.isEmpty()
                                || originRefererValidator.validate(origin, referer, allowedOrigins).valid();
        }

        private Mono<UsernameRecoveryRequestResult> deliver(
                        UsernameRecoveryRequestCommand command,
                        UsernameRecoveryIdempotencyKey idempotencyKey,
                        UsernameRecoveryIdempotencyFingerprint fingerprint,
                        UsernameRecoveryIdempotencyReservation reservation,
                        Instant now,
                        Duration ttl) {
                return workflowStatePort.load(command.authFlowSessionId())
                                .onErrorMap(Exception.class, RequestPersistenceException::new)
                                .flatMap(loadResult -> switch (loadResult.outcome()) {
                                        case LOADED -> deliverFromLoadedWorkflow(loadResult.state(), command,
                                                        idempotencyKey, fingerprint, reservation, now, ttl);
                                        case STATE_NOT_FOUND ->
                                                Mono.error(UsernameRecoveryRequestException.sessionAbsent());
                                        case SYSTEM_FAILURE ->
                                                Mono.error(UsernameRecoveryRequestException.systemFailure());
                                        default -> throw new IllegalStateException(
                                                        "unexpected workflow-state load outcome: "
                                                                        + loadResult.outcome());
                                });
        }

        private Mono<UsernameRecoveryRequestResult> deliverFromLoadedWorkflow(
                        UsernameRecoveryWorkflowState state,
                        UsernameRecoveryRequestCommand command,
                        UsernameRecoveryIdempotencyKey idempotencyKey,
                        UsernameRecoveryIdempotencyFingerprint fingerprint,
                        UsernameRecoveryIdempotencyReservation reservation,
                        Instant now,
                        Duration ttl) {
                if (!state.authFlowSessionId().equals(command.authFlowSessionId())) {
                        return Mono.error(UsernameRecoveryRequestException.systemFailure());
                }
                if (state.status() != UsernameRecoveryWorkflowStatus.ACTIVE
                                && state.status() != UsernameRecoveryWorkflowStatus.PARTIALLY_REQUESTED) {
                        return Mono.just(new UsernameRecoveryRequestResult(
                                        UsernameRecoveryRequestResult.Outcome.WORKFLOW_COMPLETED,
                                        null, null, null, null));
                }
                UsernameRecoverySessionCandidate candidate = findCandidate(state, command.schemeRef());
                if (candidate == null || !candidate.registered()) {
                        return Mono.error(UsernameRecoveryRequestException.systemFailure());
                }
                if (candidate.deliveryState() != UsernameRecoveryDeliveryState.RESERVED) {
                        return Mono.error(UsernameRecoveryRequestException.candidateNotAvailable());
                }
                return resolveEffectiveDestination(state)
                                .flatMap(effective -> invokeSender(state, candidate, effective, command,
                                                idempotencyKey, fingerprint, reservation, now, ttl))
                                .onErrorResume(EffectiveDestinationPersistenceException.class,
                                                error -> persistDefiniteFailure(state, command, idempotencyKey,
                                                                fingerprint, reservation, now, ttl));
        }

        private Mono<UsernameRecoveryEffectiveDestinationResult> resolveEffectiveDestination(
                        UsernameRecoveryWorkflowState state) {
                return effectiveDestinationPort.resolve(
                                new UsernameRecoveryEffectiveDestinationCommand(state.destination()))
                                .onErrorMap(Exception.class, EffectiveDestinationPersistenceException::new);
        }


        private Mono<UsernameRecoveryRequestResult> invokeSender(
                        UsernameRecoveryWorkflowState state,
                        UsernameRecoverySessionCandidate candidate,
                        UsernameRecoveryEffectiveDestinationResult effective,
                        UsernameRecoveryRequestCommand command,
                        UsernameRecoveryIdempotencyKey idempotencyKey,
                        UsernameRecoveryIdempotencyFingerprint fingerprint,
                        UsernameRecoveryIdempotencyReservation reservation,
                        Instant now,
                        Duration ttl) {
                String maskedDestination = destinationMasker.mask(effective.channel(), effective.destination());
                MessageDeliveryCommand deliveryCommand = buildDeliveryCommand(
                                state, candidate, effective, reservation.operationReference().value());
                return messageDeliveryPort.deliver(deliveryCommand)
                                .onErrorMap(Exception.class, RequestPersistenceException::new)
                                .flatMap(deliveryResult -> handleDeliveryOutcome(state, command, idempotencyKey,
                                                fingerprint, reservation, now, ttl, effective, maskedDestination,
                                                deliveryResult))
                                .onErrorResume(RequestPersistenceException.class,
                                                error -> persistUnknownOutcome(state, command, idempotencyKey,
                                                                fingerprint, reservation, now, ttl));
        }

        private MessageDeliveryCommand buildDeliveryCommand(
                        UsernameRecoveryWorkflowState state,
                        UsernameRecoverySessionCandidate candidate,
                        UsernameRecoveryEffectiveDestinationResult effective,
                        String correlationId) {
                MessageDeliveryChannel channel = effective.channel()
                                == UsernameRecoveryVerifiedDestination.Channel.EMAIL
                                                ? MessageDeliveryChannel.EMAIL
                                                : MessageDeliveryChannel.SMS;
                boolean email = channel == MessageDeliveryChannel.EMAIL;
                return new MessageDeliveryCommand(
                                MessageDeliveryType.USERNAME_RECOVERY,
                                MessageDeliveryScenario.USERNAME_RECOVERY,
                                channel,
                                effective.destination(),
                                email ? emailSubject : null,
                                email ? emailBody : smsBody,
                                null,
                                Map.of("username", candidate.userName()),
                                state.language().toLanguageTag(),
                                correlationId);
        }

        private Mono<UsernameRecoveryRequestResult> handleDeliveryOutcome(
                        UsernameRecoveryWorkflowState state,
                        UsernameRecoveryRequestCommand command,
                        UsernameRecoveryIdempotencyKey idempotencyKey,
                        UsernameRecoveryIdempotencyFingerprint fingerprint,
                        UsernameRecoveryIdempotencyReservation reservation,
                        Instant now,
                        Duration ttl,
                        UsernameRecoveryEffectiveDestinationResult effective,
                        String maskedDestination,
                        MessageDeliveryResult deliveryResult) {
                if (deliveryResult == null || deliveryResult.status() == null) {
                        return Mono.error(UsernameRecoveryRequestException.systemFailure());
                }
                return switch (deliveryResult.status()) {
                        case ACCEPTED -> persistAccepted(state, command, idempotencyKey, fingerprint,
                                        reservation, now, ttl, effective, maskedDestination, deliveryResult);
                        case FAILED -> persistDefiniteFailure(state, command, idempotencyKey, fingerprint,
                                        reservation, now, ttl);
                        case UNKNOWN_OUTCOME -> persistUnknownOutcome(state, command, idempotencyKey, fingerprint,
                                        reservation, now, ttl);
                };
        }

        private Mono<UsernameRecoveryRequestResult> persistAccepted(
                        UsernameRecoveryWorkflowState state,
                        UsernameRecoveryRequestCommand command,
                        UsernameRecoveryIdempotencyKey idempotencyKey,
                        UsernameRecoveryIdempotencyFingerprint fingerprint,
                        UsernameRecoveryIdempotencyReservation reservation,
                        Instant now,
                        Duration ttl,
                        UsernameRecoveryEffectiveDestinationResult effective,
                        String maskedDestination,
                        MessageDeliveryResult deliveryResult) {
                String senderReference = nonBlank(deliveryResult.providerMessageId())
                                ? deliveryResult.providerMessageId()
                                : reservation.operationReference().value();
                UsernameRecoveryWorkflowState requested =
                                state.markRequested(command.schemeRef(), senderReference, now);
                return updateWorkflowState(state, requested)
                                .flatMap(result -> switch (result.outcome()) {
                                        case APPLIED -> completeAccepted(result.state(), command, idempotencyKey,
                                                        fingerprint, reservation, now, ttl, effective,
                                                        maskedDestination);
                                        case STATE_NOT_FOUND, REVISION_CONFLICT, SYSTEM_FAILURE ->
                                                recordUnknownAfterAcceptance(command, idempotencyKey, fingerprint,
                                                                reservation, now, ttl);
                                        default -> throw new IllegalStateException(
                                                        "unexpected workflow-state update outcome: "
                                                                        + result.outcome());
                                });
        }

        private Mono<UsernameRecoveryRequestResult> completeAccepted(
                        UsernameRecoveryWorkflowState requested,
                        UsernameRecoveryRequestCommand command,
                        UsernameRecoveryIdempotencyKey idempotencyKey,
                        UsernameRecoveryIdempotencyFingerprint fingerprint,
                        UsernameRecoveryIdempotencyReservation reservation,
                        Instant now,
                        Duration ttl,
                        UsernameRecoveryEffectiveDestinationResult effective,
                        String maskedDestination) {
                ReplaySnapshot snapshot = UsernameRecoveryRequestReplayCodec.encode(requested.authFlowSessionId());
                UsernameRecoveryIdempotencyPort.CompleteCommand completeCommand =
                                new UsernameRecoveryIdempotencyPort.CompleteCommand(
                                                UsernameRecoveryIdempotencyAction.REQUEST,
                                                idempotencyKey,
                                                fingerprint,
                                                reservation.operationReference(),
                                                snapshot,
                                                now,
                                                ttl);
                return idempotencyPort.complete(completeCommand)
                                .onErrorMap(Exception.class, RequestPersistenceException::new)
                                .map(record -> new UsernameRecoveryRequestResult(
                                                UsernameRecoveryRequestResult.Outcome.REQUEST_ACCEPTED,
                                                requested.status(),
                                                command.schemeRef(),
                                                maskedDestination,
                                                destinationType(effective.channel())))
                                .onErrorResume(RequestPersistenceException.class,
                                                error -> recordUnknownAfterAcceptance(command, idempotencyKey,
                                                                fingerprint, reservation, now, ttl));
        }

        private Mono<UsernameRecoveryRequestResult> persistDefiniteFailure(
                        UsernameRecoveryWorkflowState state,
                        UsernameRecoveryRequestCommand command,
                        UsernameRecoveryIdempotencyKey idempotencyKey,
                        UsernameRecoveryIdempotencyFingerprint fingerprint,
                        UsernameRecoveryIdempotencyReservation reservation,
                        Instant now,
                        Duration ttl) {
                UsernameRecoveryWorkflowState failed = state.markFailed(command.schemeRef());
                return updateWorkflowState(state, failed)
                                .flatMap(result -> switch (result.outcome()) {
                                        case APPLIED -> completeDefiniteFailure(result.state(), command,
                                                        idempotencyKey, fingerprint, reservation, now, ttl);
                                        case STATE_NOT_FOUND, REVISION_CONFLICT, SYSTEM_FAILURE ->
                                                recordUnknownAfterAcceptance(command, idempotencyKey, fingerprint,
                                                                reservation, now, ttl);
                                        default -> throw new IllegalStateException(
                                                        "unexpected workflow-state update outcome: "
                                                                        + result.outcome());
                                });
        }

        private Mono<UsernameRecoveryRequestResult> completeDefiniteFailure(
                        UsernameRecoveryWorkflowState failed,
                        UsernameRecoveryRequestCommand command,
                        UsernameRecoveryIdempotencyKey idempotencyKey,
                        UsernameRecoveryIdempotencyFingerprint fingerprint,
                        UsernameRecoveryIdempotencyReservation reservation,
                        Instant now,
                        Duration ttl) {
                ReplaySnapshot snapshot = UsernameRecoveryRequestReplayCodec.encode(failed.authFlowSessionId());
                UsernameRecoveryIdempotencyPort.CompleteCommand completeCommand =
                                new UsernameRecoveryIdempotencyPort.CompleteCommand(
                                                UsernameRecoveryIdempotencyAction.REQUEST,
                                                idempotencyKey,
                                                fingerprint,
                                                reservation.operationReference(),
                                                snapshot,
                                                now,
                                                ttl);
                return idempotencyPort.complete(completeCommand)
                                .onErrorMap(Exception.class, RequestPersistenceException::new)
                                .map(record -> new UsernameRecoveryRequestResult(
                                                UsernameRecoveryRequestResult.Outcome.SYSTEM_FAILED,
                                                null, null, null, null))
                                .onErrorResume(RequestPersistenceException.class,
                                                error -> recordUnknownAfterAcceptance(command, idempotencyKey,
                                                                fingerprint, reservation, now, ttl));
        }

        private Mono<UsernameRecoveryRequestResult> persistUnknownOutcome(
                        UsernameRecoveryWorkflowState state,
                        UsernameRecoveryRequestCommand command,
                        UsernameRecoveryIdempotencyKey idempotencyKey,
                        UsernameRecoveryIdempotencyFingerprint fingerprint,
                        UsernameRecoveryIdempotencyReservation reservation,
                        Instant now,
                        Duration ttl) {
                UsernameRecoveryWorkflowState unknown = state.markUnknownOutcome(command.schemeRef());
                return updateWorkflowState(state, unknown)
                                .flatMap(result -> switch (result.outcome()) {
                                        case APPLIED, STATE_NOT_FOUND, REVISION_CONFLICT, SYSTEM_FAILURE ->
                                                recordUnknownAfterAcceptance(command, idempotencyKey, fingerprint,
                                                                reservation, now, ttl);
                                        default -> throw new IllegalStateException(
                                                        "unexpected workflow-state update outcome: "
                                                                        + result.outcome());
                                });
        }

        private Mono<com.bct.ngtpa.apiservice.application.port.out.UsernameRecoveryWorkflowStateMutationResult>
                        updateWorkflowState(
                                        UsernameRecoveryWorkflowState current,
                                        UsernameRecoveryWorkflowState updated) {
                UsernameRecoveryWorkflowStateMutationCommand mutation =
                                new UsernameRecoveryWorkflowStateMutationCommand(
                                                current.authFlowSessionId(), current.revision(), updated);
                return workflowStatePort.update(mutation)
                                .onErrorMap(Exception.class, RequestPersistenceException::new);
        }

        /**
         * Post-acceptance (or post-uncertainty) persistence safety: the sender has
         * already been called,
         * so ordinary success is only returned when the required state is committed.
         * Every other path
         * records a stable unknown outcome through the ownership-checked idempotency
         * boundary and never
         * calls the sender again.
         */
        private Mono<UsernameRecoveryRequestResult> recordUnknownAfterAcceptance(
                        UsernameRecoveryRequestCommand command,
                        UsernameRecoveryIdempotencyKey idempotencyKey,
                        UsernameRecoveryIdempotencyFingerprint fingerprint,
                        UsernameRecoveryIdempotencyReservation reservation,
                        Instant now,
                        Duration ttl) {
                return recordStableUnknownOutcome(idempotencyKey, fingerprint, reservation, now, ttl)
                                .map(record -> new UsernameRecoveryRequestResult(
                                                UsernameRecoveryRequestResult.Outcome.UNKNOWN_OUTCOME, null, null, null,
                                                null));
        }

        private Mono<UsernameRecoveryIdempotencyRecord> recordStableUnknownOutcome(
                        UsernameRecoveryIdempotencyKey idempotencyKey,
                        UsernameRecoveryIdempotencyFingerprint fingerprint,
                        UsernameRecoveryIdempotencyReservation reservation,
                        Instant now,
                        Duration ttl) {
                UsernameRecoveryIdempotencyPort.UnknownOutcomeCommand unknownOutcomeCommand = new UsernameRecoveryIdempotencyPort.UnknownOutcomeCommand(
                                UsernameRecoveryIdempotencyAction.REQUEST,
                                idempotencyKey,
                                fingerprint,
                                reservation.operationReference(),
                                now,
                                ttl);
                return idempotencyPort.recordUnknownOutcome(unknownOutcomeCommand)
                                .onErrorMap(Exception.class, RequestPersistenceException::new);
        }

        private Mono<UsernameRecoveryRequestResult> replay(
                        UsernameRecoveryRequestCommand command,
                        UsernameRecoveryIdempotencyKey idempotencyKey,
                        Instant now) {
                return idempotencyPort.resolve(UsernameRecoveryIdempotencyAction.REQUEST, idempotencyKey)
                                .onErrorMap(Exception.class, RequestPersistenceException::new)
                                .flatMap(optional -> {
                                        if (optional.isEmpty() || optional.get().replaySnapshot() == null) {
                                                return Mono.error(UsernameRecoveryRequestException.systemFailure());
                                        }
                                        UsernameRecoveryRequestReplayCodec.DecodeResult decoded =
                                                        UsernameRecoveryRequestReplayCodec.decode(optional.get().replaySnapshot());
                                        if (!decoded.decoded()
                                                        || !decoded.authFlowSessionId().equals(command.authFlowSessionId())) {
                                                return Mono.error(UsernameRecoveryRequestException.systemFailure());
                                        }
                                        return authFlowSessionPort.loadSnapshot(decoded.authFlowSessionId())
                                                        .onErrorMap(Exception.class, RequestPersistenceException::new)
                                                        .flatMap(optionalEnvelope -> optionalEnvelope
                                                                        .map(envelope -> validateReplayEnvelope(envelope, command, now))
                                                                        .orElseGet(() -> Mono.error(
                                                                                        UsernameRecoveryRequestException.systemFailure())));
                                });
        }

        private Mono<UsernameRecoveryRequestResult> validateReplayEnvelope(
                        AuthFlowSessionSnapshot envelope,
                        UsernameRecoveryRequestCommand command,
                        Instant now) {
                if (!envelope.session().authFlowSessionId().equals(command.authFlowSessionId())
                                || envelope.session().scenario() != AuthFlowScenario.USERNAME_RECOVERY
                                || envelope.session().isExpired(now)
                                || envelope.session().status() != AuthFlowStatus.STARTED) {
                        return Mono.error(UsernameRecoveryRequestException.systemFailure());
                }
                return workflowStatePort.load(command.authFlowSessionId())
                                .onErrorMap(Exception.class, RequestPersistenceException::new)
                                .flatMap(load -> switch (load.outcome()) {
                                        case LOADED -> rebuildReplay(load.state(), command);
                                        case STATE_NOT_FOUND, SYSTEM_FAILURE ->
                                                Mono.error(UsernameRecoveryRequestException.systemFailure());
                                        default -> throw new IllegalStateException(
                                                        "unexpected workflow-state load outcome: " + load.outcome());
                                });
        }

        private Mono<UsernameRecoveryRequestResult> rebuildReplay(
                        UsernameRecoveryWorkflowState state,
                        UsernameRecoveryRequestCommand command) {
                if (!state.authFlowSessionId().equals(command.authFlowSessionId())) {
                        return Mono.error(UsernameRecoveryRequestException.systemFailure());
                }
                UsernameRecoverySessionCandidate candidate = findCandidate(state, command.schemeRef());
                if (candidate == null || !candidate.registered()) {
                        return Mono.error(UsernameRecoveryRequestException.systemFailure());
                }
                return switch (candidate.deliveryState()) {
                        case REQUESTED -> rebuildAcceptedReplay(state, command);
                        case FAILED -> Mono.just(new UsernameRecoveryRequestResult(
                                        UsernameRecoveryRequestResult.Outcome.SYSTEM_FAILED, null, null, null, null));
                        case UNKNOWN_OUTCOME -> Mono.just(new UsernameRecoveryRequestResult(
                                        UsernameRecoveryRequestResult.Outcome.UNKNOWN_OUTCOME, null, null, null, null));
                        default -> Mono.error(UsernameRecoveryRequestException.systemFailure());
                };
        }

        private Mono<UsernameRecoveryRequestResult> rebuildAcceptedReplay(
                        UsernameRecoveryWorkflowState state,
                        UsernameRecoveryRequestCommand command) {
                if (state.status() != UsernameRecoveryWorkflowStatus.PARTIALLY_REQUESTED
                                && state.status() != UsernameRecoveryWorkflowStatus.COMPLETED) {
                        return Mono.error(UsernameRecoveryRequestException.systemFailure());
                }
                return resolveEffectiveDestination(state)
                                .map(effective -> new UsernameRecoveryRequestResult(
                                                UsernameRecoveryRequestResult.Outcome.REQUEST_ACCEPTED,
                                                state.status(), command.schemeRef(),
                                                destinationMasker.mask(effective.channel(), effective.destination()),
                                                destinationType(effective.channel())));
        }


        private UsernameRecoverySessionCandidate findCandidate(
                        UsernameRecoveryWorkflowState state, UsernameRecoverySchemeRef schemeRef) {
                return state.candidates().stream()
                                .filter(candidate -> candidate.schemeRef().equals(schemeRef))
                                .findFirst()
                                .orElse(null);
        }


        private UsernameRecoveryDestinationType destinationType(
                        UsernameRecoveryVerifiedDestination.Channel channel) {
                return channel == UsernameRecoveryVerifiedDestination.Channel.EMAIL
                                ? UsernameRecoveryDestinationType.EMAIL
                                : UsernameRecoveryDestinationType.MOBILE;
        }

        private UsernameRecoveryIdempotencyOperationReference generateOperationReference() {
                return new UsernameRecoveryIdempotencyOperationReference(
                                "op_" + Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes(18)));
        }

        private byte[] randomBytes(int length) {
                byte[] bytes = new byte[length];
                secureRandom.nextBytes(bytes);
                return bytes;
        }

        private static boolean nonBlank(String value) {
                return value != null && !value.isBlank();
        }

        private static String requireNonBlank(String value, String name) {
                if (!nonBlank(value)) {
                        throw new IllegalArgumentException(name + " must not be blank");
                }
                return value;
        }

        private static final class EffectiveDestinationPersistenceException extends RuntimeException {
                private EffectiveDestinationPersistenceException(Throwable cause) {
                        super("Username recovery effective destination resolution failed", cause);
                }
        }

        private static final class RequestPersistenceException extends RuntimeException {
                private RequestPersistenceException(Throwable cause) {
                        super("Username recovery request persistence failed", cause);
                }
        }
}
