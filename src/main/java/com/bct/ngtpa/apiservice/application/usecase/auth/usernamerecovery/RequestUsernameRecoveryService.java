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
import com.bct.ngtpa.apiservice.application.port.out.UsernameRecoverySessionLoadResult;
import com.bct.ngtpa.apiservice.application.port.out.UsernameRecoverySessionMutationCommand;
import com.bct.ngtpa.apiservice.application.port.out.UsernameRecoverySessionMutationOutcome;
import com.bct.ngtpa.apiservice.application.port.out.UsernameRecoverySessionPort;
import com.bct.ngtpa.apiservice.application.service.security.csrf.OriginRefererValidator;
import com.bct.ngtpa.apiservice.domain.model.UsernameRecoveryDeliveryState;
import com.bct.ngtpa.apiservice.domain.model.UsernameRecoverySchemeRef;
import com.bct.ngtpa.apiservice.domain.model.UsernameRecoverySession;
import com.bct.ngtpa.apiservice.domain.model.UsernameRecoverySessionCandidate;
import com.bct.ngtpa.apiservice.domain.model.UsernameRecoverySessionId;
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
        private final UsernameRecoverySessionPort sessionPort;
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
                        UsernameRecoverySessionPort sessionPort,
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
                this.sessionPort = Objects.requireNonNull(sessionPort, "sessionPort must not be null");
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
                                sessionIdOf(command), command.schemeRef(), now);
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
                return sessionPort.load(sessionIdOf(command))
                                .onErrorMap(Exception.class, RequestPersistenceException::new)
                                .flatMap(loadResult -> switch (loadResult.outcome()) {
                                        case LOADED -> deliverFromLoadedSession(loadResult.session(), command,
                                                        idempotencyKey, fingerprint, reservation, now, ttl);
                                        case SESSION_NOT_FOUND ->
                                                Mono.error(UsernameRecoveryRequestException.sessionAbsent());
                                        case SYSTEM_FAILURE ->
                                                Mono.error(UsernameRecoveryRequestException.systemFailure());
                                        default -> throw new IllegalStateException(
                                                        "unexpected session load outcome: " + loadResult.outcome());
                                });
        }

        private Mono<UsernameRecoveryRequestResult> deliverFromLoadedSession(
                        UsernameRecoverySession session,
                        UsernameRecoveryRequestCommand command,
                        UsernameRecoveryIdempotencyKey idempotencyKey,
                        UsernameRecoveryIdempotencyFingerprint fingerprint,
                        UsernameRecoveryIdempotencyReservation reservation,
                        Instant now,
                        Duration ttl) {
                if (session.isExpired(now)) {
                        return Mono.error(UsernameRecoveryRequestException.sessionExpired());
                }
                if (session.isTerminal()) {
                        return Mono.just(new UsernameRecoveryRequestResult(
                                        UsernameRecoveryRequestResult.Outcome.WORKFLOW_COMPLETED, null, null, null,
                                        null));
                }
                UsernameRecoverySessionCandidate candidate = findCandidate(session, command.schemeRef());
                if (candidate == null || !candidate.registered()) {
                        // Defensive: the reservation boundary already proved ownership and eligibility.
                        return Mono.error(UsernameRecoveryRequestException.systemFailure());
                }
                if (candidate.deliveryState() != UsernameRecoveryDeliveryState.RESERVED) {
                        // The reservation was superseded or no longer owned by this caller; never
                        // deliver.
                        return Mono.error(UsernameRecoveryRequestException.candidateNotAvailable());
                }
                return resolveEffectiveDestination(session)
                                .flatMap(effective -> invokeSender(session, candidate, effective, command,
                                                idempotencyKey, fingerprint, reservation, now, ttl))
                                .onErrorResume(RequestPersistenceException.class,
                                                error -> persistDefiniteFailure(session, command, idempotencyKey,
                                                                fingerprint, reservation, now, ttl));
        }

        private Mono<UsernameRecoveryEffectiveDestinationResult> resolveEffectiveDestination(
                        UsernameRecoverySession session) {
                return effectiveDestinationPort.resolve(
                                new UsernameRecoveryEffectiveDestinationCommand(session.destination()))
                                .onErrorMap(Exception.class, RequestPersistenceException::new);
        }

        private Mono<UsernameRecoveryRequestResult> invokeSender(
                        UsernameRecoverySession session,
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
                                session, candidate, effective, reservation.operationReference().value());
                return messageDeliveryPort.deliver(deliveryCommand)
                                .onErrorMap(Exception.class, RequestPersistenceException::new)
                                .flatMap(deliveryResult -> handleDeliveryOutcome(session, command, idempotencyKey,
                                                fingerprint, reservation, now, ttl, effective, maskedDestination,
                                                deliveryResult))
                                .onErrorResume(RequestPersistenceException.class,
                                                error -> persistUnknownOutcome(session, command, idempotencyKey,
                                                                fingerprint, reservation, now, ttl));
        }

        private MessageDeliveryCommand buildDeliveryCommand(
                        UsernameRecoverySession session,
                        UsernameRecoverySessionCandidate candidate,
                        UsernameRecoveryEffectiveDestinationResult effective,
                        String correlationId) {
                MessageDeliveryChannel channel = effective
                                .channel() == UsernameRecoveryVerifiedDestination.Channel.EMAIL
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
                                session.language().toLanguageTag(),
                                correlationId);
        }

        private Mono<UsernameRecoveryRequestResult> handleDeliveryOutcome(
                        UsernameRecoverySession session,
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
                        // A null/stateless delivery result is a programming defect, never a delivery
                        // outcome.
                        return Mono.error(UsernameRecoveryRequestException.systemFailure());
                }
                return switch (deliveryResult.status()) {
                        case ACCEPTED -> persistAccepted(session, command, idempotencyKey, fingerprint,
                                        reservation, now, ttl, effective, maskedDestination, deliveryResult);
                        case FAILED -> persistDefiniteFailure(session, command, idempotencyKey, fingerprint,
                                        reservation, now, ttl);
                        case UNKNOWN_OUTCOME -> persistUnknownOutcome(session, command, idempotencyKey, fingerprint,
                                        reservation, now, ttl);
                };
        }

        private Mono<UsernameRecoveryRequestResult> persistAccepted(
                        UsernameRecoverySession session,
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
                UsernameRecoverySession requested = session.markRequested(command.schemeRef(), senderReference, now);
                UsernameRecoverySessionMutationCommand mutation = new UsernameRecoverySessionMutationCommand(
                                session.sessionId(), session.revision(), requested);
                return sessionPort.update(mutation)
                                .onErrorMap(Exception.class, RequestPersistenceException::new)
                                .flatMap(result -> switch (result.outcome()) {
                                        case APPLIED ->
                                                completeAccepted(requested, command, idempotencyKey, fingerprint,
                                                                reservation, now, ttl, effective, maskedDestination);
                                        case SESSION_NOT_FOUND, REVISION_CONFLICT, SYSTEM_FAILURE ->
                                                recordUnknownAfterAcceptance(command, idempotencyKey, fingerprint,
                                                                reservation, now, ttl);
                                        default -> throw new IllegalStateException(
                                                        "unexpected session update outcome: " + result.outcome());
                                });
        }

        private Mono<UsernameRecoveryRequestResult> completeAccepted(
                        UsernameRecoverySession requested,
                        UsernameRecoveryRequestCommand command,
                        UsernameRecoveryIdempotencyKey idempotencyKey,
                        UsernameRecoveryIdempotencyFingerprint fingerprint,
                        UsernameRecoveryIdempotencyReservation reservation,
                        Instant now,
                        Duration ttl,
                        UsernameRecoveryEffectiveDestinationResult effective,
                        String maskedDestination) {
                ReplaySnapshot snapshot = UsernameRecoveryRequestReplayCodec.encode(requested.sessionId());
                UsernameRecoveryIdempotencyPort.CompleteCommand completeCommand = new UsernameRecoveryIdempotencyPort.CompleteCommand(
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
                                                                fingerprint,
                                                                reservation, now, ttl));
        }

        private Mono<UsernameRecoveryRequestResult> persistDefiniteFailure(
                        UsernameRecoverySession session,
                        UsernameRecoveryRequestCommand command,
                        UsernameRecoveryIdempotencyKey idempotencyKey,
                        UsernameRecoveryIdempotencyFingerprint fingerprint,
                        UsernameRecoveryIdempotencyReservation reservation,
                        Instant now,
                        Duration ttl) {
                UsernameRecoverySession failed = session.markFailed(command.schemeRef());
                UsernameRecoverySessionMutationCommand mutation = new UsernameRecoverySessionMutationCommand(
                                session.sessionId(), session.revision(), failed);
                return sessionPort.update(mutation)
                                .onErrorMap(Exception.class, RequestPersistenceException::new)
                                .flatMap(result -> switch (result.outcome()) {
                                        case APPLIED ->
                                                completeDefiniteFailure(failed, command, idempotencyKey, fingerprint,
                                                                reservation, now, ttl);
                                        case SESSION_NOT_FOUND, REVISION_CONFLICT, SYSTEM_FAILURE ->
                                                recordUnknownAfterAcceptance(command, idempotencyKey, fingerprint,
                                                                reservation, now, ttl);
                                        default -> throw new IllegalStateException(
                                                        "unexpected session update outcome: " + result.outcome());
                                });
        }

        private Mono<UsernameRecoveryRequestResult> completeDefiniteFailure(
                        UsernameRecoverySession failed,
                        UsernameRecoveryRequestCommand command,
                        UsernameRecoveryIdempotencyKey idempotencyKey,
                        UsernameRecoveryIdempotencyFingerprint fingerprint,
                        UsernameRecoveryIdempotencyReservation reservation,
                        Instant now,
                        Duration ttl) {
                ReplaySnapshot snapshot = UsernameRecoveryRequestReplayCodec.encode(failed.sessionId());
                UsernameRecoveryIdempotencyPort.CompleteCommand completeCommand = new UsernameRecoveryIdempotencyPort.CompleteCommand(
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
                                                UsernameRecoveryRequestResult.Outcome.SYSTEM_FAILED, null, null, null,
                                                null))
                                .onErrorResume(RequestPersistenceException.class,
                                                error -> recordUnknownAfterAcceptance(command, idempotencyKey,
                                                                fingerprint,
                                                                reservation, now, ttl));
        }

        private Mono<UsernameRecoveryRequestResult> persistUnknownOutcome(
                        UsernameRecoverySession session,
                        UsernameRecoveryRequestCommand command,
                        UsernameRecoveryIdempotencyKey idempotencyKey,
                        UsernameRecoveryIdempotencyFingerprint fingerprint,
                        UsernameRecoveryIdempotencyReservation reservation,
                        Instant now,
                        Duration ttl) {
                UsernameRecoverySession unknown = session.markUnknownOutcome(command.schemeRef());
                UsernameRecoverySessionMutationCommand mutation = new UsernameRecoverySessionMutationCommand(
                                session.sessionId(), session.revision(), unknown);
                return sessionPort.update(mutation)
                                .onErrorMap(Exception.class, RequestPersistenceException::new)
                                .flatMap(result -> switch (result.outcome()) {
                                        case APPLIED, SESSION_NOT_FOUND, REVISION_CONFLICT, SYSTEM_FAILURE ->
                                                recordUnknownAfterAcceptance(command, idempotencyKey, fingerprint,
                                                                reservation, now, ttl);
                                        default -> throw new IllegalStateException(
                                                        "unexpected session update outcome: " + result.outcome());
                                });
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
                                        if (optional.isEmpty()) {
                                                return Mono.error(UsernameRecoveryRequestException.systemFailure());
                                        }
                                        ReplaySnapshot snapshot = optional.get().replaySnapshot();
                                        if (snapshot == null) {
                                                return Mono.error(UsernameRecoveryRequestException.systemFailure());
                                        }
                                        UsernameRecoveryRequestReplayCodec.DecodeResult decoded = UsernameRecoveryRequestReplayCodec
                                                        .decode(snapshot);
                                        if (!decoded.decoded()) {
                                                return Mono.error(UsernameRecoveryRequestException.systemFailure());
                                        }
                                        return sessionPort.load(decoded.sessionId())
                                                        .onErrorMap(Exception.class, RequestPersistenceException::new)
                                                        .flatMap(loadResult -> switch (loadResult.outcome()) {
                                                                case LOADED -> rebuildReplay(loadResult.session(),
                                                                                command, now);
                                                                case SESSION_NOT_FOUND ->
                                                                        Mono.error(UsernameRecoveryRequestException
                                                                                        .systemFailure());
                                                                case SYSTEM_FAILURE ->
                                                                        Mono.error(UsernameRecoveryRequestException
                                                                                        .systemFailure());
                                                                default -> throw new IllegalStateException(
                                                                                "unexpected session load outcome: "
                                                                                                + loadResult.outcome());
                                                        });
                                });
        }

        private Mono<UsernameRecoveryRequestResult> rebuildReplay(
                        UsernameRecoverySession session,
                        UsernameRecoveryRequestCommand command,
                        Instant now) {
                UsernameRecoverySessionCandidate candidate = findCandidate(session, command.schemeRef());
                if (candidate == null || !candidate.registered()) {
                        return Mono.error(UsernameRecoveryRequestException.systemFailure());
                }
                return switch (candidate.deliveryState()) {
                        case REQUESTED -> rebuildAcceptedReplay(session, command, now);
                        case FAILED -> Mono.just(new UsernameRecoveryRequestResult(
                                        UsernameRecoveryRequestResult.Outcome.SYSTEM_FAILED, null, null, null, null));
                        case UNKNOWN_OUTCOME -> Mono.just(new UsernameRecoveryRequestResult(
                                        UsernameRecoveryRequestResult.Outcome.UNKNOWN_OUTCOME, null, null, null, null));
                        default -> Mono.error(UsernameRecoveryRequestException.systemFailure());
                };
        }

        private Mono<UsernameRecoveryRequestResult> rebuildAcceptedReplay(
                        UsernameRecoverySession session,
                        UsernameRecoveryRequestCommand command,
                        Instant now) {
                if (session.isExpired(now)
                                || (session.status() != UsernameRecoveryWorkflowStatus.PARTIALLY_REQUESTED
                                                && session.status() != UsernameRecoveryWorkflowStatus.COMPLETED)) {
                        return Mono.error(UsernameRecoveryRequestException.systemFailure());
                }
                return resolveEffectiveDestination(session)
                                .map(effective -> new UsernameRecoveryRequestResult(
                                                UsernameRecoveryRequestResult.Outcome.REQUEST_ACCEPTED,
                                                session.status(),
                                                command.schemeRef(),
                                                destinationMasker.mask(effective.channel(), effective.destination()),
                                                destinationType(effective.channel())));
        }

        /**
         * Converts the shared auth-flow session id carried by the committed Request
         * command into the
         * Username Recovery domain session id. Both value objects enforce the same safe
         * opaque pattern
         * ({@code ^[A-Za-z0-9_-]+$}), so the conversion is value-preserving and fails
         * closed on an
         * unsafe value.
         */
        private UsernameRecoverySessionId sessionIdOf(UsernameRecoveryRequestCommand command) {
                return new UsernameRecoverySessionId(command.authFlowSessionId().value());
        }

        private UsernameRecoverySessionCandidate findCandidate(
                        UsernameRecoverySession session, UsernameRecoverySchemeRef schemeRef) {
                return session.candidates().stream()
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

        private static final class RequestPersistenceException extends RuntimeException {
                private RequestPersistenceException(Throwable cause) {
                        super("Username recovery request persistence failed", cause);
                }
        }
}
