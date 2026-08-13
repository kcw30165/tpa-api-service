package com.bct.ngtpa.apiservice.application.usecase.auth.usernamerecovery;

import com.bct.ngtpa.apiservice.application.dto.auth.usernamerecovery.UsernameRecoveryDestinationType;
import com.bct.ngtpa.apiservice.application.dto.auth.usernamerecovery.UsernameRecoveryEffectiveDestinationCommand;
import com.bct.ngtpa.apiservice.application.dto.auth.usernamerecovery.UsernameRecoveryEffectiveDestinationResult;
import com.bct.ngtpa.apiservice.application.dto.auth.usernamerecovery.UsernameRecoveryRequestCommand;
import com.bct.ngtpa.apiservice.application.dto.auth.usernamerecovery.UsernameRecoveryRequestResult;
import com.bct.ngtpa.apiservice.application.dto.auth.usernamerecovery.context.UsernameRecoveryAuthFlowContext;
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
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSession;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionMutationOutcome;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.RevisionedAuthFlowSessionMutationCommand;
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
import com.bct.ngtpa.apiservice.application.service.security.csrf.OriginRefererValidator;
import com.bct.ngtpa.apiservice.application.service.auth.usernamerecovery.context.UsernameRecoveryAuthFlowContextBinding;
import com.bct.ngtpa.apiservice.application.usecase.session.authflow.CsrfTokenValidationService;
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
import lombok.extern.slf4j.Slf4j;
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
@Slf4j
public final class RequestUsernameRecoveryService implements RequestUsernameRecoveryUseCase {

        private final UsernameRecoveryRequestFingerprintGenerator fingerprintGenerator;
        private final UsernameRecoveryIdempotencyPort idempotencyPort;
        private final UsernameRecoveryAuthFlowContextBinding contextBinding;
        private final CsrfTokenValidationService csrfTokenValidationService;
        private final UsernameRecoveryEffectiveDestinationPort effectiveDestinationPort;
        private final AuthFlowSessionPort authFlowSessionPort;
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
                        UsernameRecoveryAuthFlowContextBinding contextBinding,
                        CsrfTokenValidationService csrfTokenValidationService,
                        UsernameRecoveryEffectiveDestinationPort effectiveDestinationPort,
                        AuthFlowSessionPort authFlowSessionPort,
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
                this.contextBinding = Objects.requireNonNull(contextBinding,
                                "contextBinding must not be null");
                this.csrfTokenValidationService = Objects.requireNonNull(csrfTokenValidationService,
                                "csrfTokenValidationService must not be null");
                this.effectiveDestinationPort = Objects.requireNonNull(
                                effectiveDestinationPort, "effectiveDestinationPort must not be null");
                this.authFlowSessionPort = Objects.requireNonNull(authFlowSessionPort, "authFlowSessionPort must not be null");
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
                                .doOnError(RequestPersistenceException.class, error -> log.error(
                                                "event=username-recovery.request.persistence-failed errorType={} causeType={}",
                                                error.getClass().getSimpleName(),
                                                error.getCause() == null
                                                                ? "none"
                                                                : error.getCause().getClass().getSimpleName(),
                                                error))
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
                        UsernameRecoveryRequestCommand command, String rawCsrfToken,
                        String origin, String referer,
                        UsernameRecoveryIdempotencyKey idempotencyKey,
                        UsernameRecoveryIdempotencyFingerprint fingerprint,
                        UsernameRecoveryIdempotencyReservation reservation,
                        Instant now, Duration ttl) {
                if (!originRefererAllowed(origin, referer)) {
                        return Mono.error(UsernameRecoveryRequestException.securityInvalid());
                }
                return authFlowSessionPort.loadSnapshot(command.authFlowSessionId())
                                .onErrorMap(Exception.class, RequestPersistenceException::new)
                                .flatMap(optional -> optional
                                                .map(snapshot -> reserveCandidate(snapshot, command, rawCsrfToken,
                                                                idempotencyKey, fingerprint, reservation, now, ttl))
                                                .orElseGet(() -> Mono.error(
                                                                UsernameRecoveryRequestException.sessionAbsent())));
        }

        private Mono<UsernameRecoveryRequestResult> reserveCandidate(
                        AuthFlowSessionSnapshot snapshot,
                        UsernameRecoveryRequestCommand command,
                        String rawCsrfToken,
                        UsernameRecoveryIdempotencyKey idempotencyKey,
                        UsernameRecoveryIdempotencyFingerprint fingerprint,
                        UsernameRecoveryIdempotencyReservation reservation,
                        Instant now, Duration ttl) {
                AuthFlowSession session = snapshot.session();
                if (session.scenario() != AuthFlowScenario.USERNAME_RECOVERY) {
                        return Mono.error(UsernameRecoveryRequestException.sessionAbsent());
                }
                if (session.isExpired(now)) {
                        return Mono.error(UsernameRecoveryRequestException.sessionExpired());
                }
                if (session.isTerminal() || session.status() != AuthFlowStatus.STARTED) {
                        return Mono.just(new UsernameRecoveryRequestResult(
                                        UsernameRecoveryRequestResult.Outcome.WORKFLOW_COMPLETED,
                                        null, null, null, null));
                }
                try {
                        if (!csrfTokenValidationService.validate(rawCsrfToken, session.csrfTokenHash()).valid()) {
                                return Mono.error(UsernameRecoveryRequestException.securityInvalid());
                        }
                } catch (RuntimeException error) {
                        return Mono.error(UsernameRecoveryRequestException.systemFailure(error));
                }
                final UsernameRecoveryAuthFlowContext context;
                try {
                        context = contextBinding.load(session);
                } catch (RuntimeException error) {
                        return Mono.error(UsernameRecoveryRequestException.systemFailure(error));
                }
                UsernameRecoverySessionCandidate candidate = findCandidate(context, command.schemeRef());
                if (candidate == null) {
                        return Mono.just(new UsernameRecoveryRequestResult(
                                        UsernameRecoveryRequestResult.Outcome.INVALID_SCHEME_REF,
                                        null, null, null, null));
                }
                if (!candidate.registered()) {
                        return Mono.just(new UsernameRecoveryRequestResult(
                                        UsernameRecoveryRequestResult.Outcome.UNREGISTERED_CANDIDATE,
                                        null, null, null, null));
                }
                if (candidate.deliveryState() == UsernameRecoveryDeliveryState.REQUESTED) {
                        return Mono.just(new UsernameRecoveryRequestResult(
                                        UsernameRecoveryRequestResult.Outcome.ALREADY_REQUESTED,
                                        null, null, null, null));
                }
                if (candidate.deliveryState() != UsernameRecoveryDeliveryState.AVAILABLE) {
                        return Mono.error(UsernameRecoveryRequestException.candidateNotAvailable());
                }
                UsernameRecoveryAuthFlowContext reserved = context.reserve(command.schemeRef());
                AuthFlowSession updated = contextBinding.bind(session, reserved, now);
                return authFlowSessionPort.updateRevisioned(
                                        new RevisionedAuthFlowSessionMutationCommand(snapshot, updated))
                                .onErrorMap(Exception.class, RequestPersistenceException::new)
                                .flatMap(result -> switch (result.outcome()) {
                                        case APPLIED -> deliver(result.snapshot(), reserved, command,
                                                        idempotencyKey, fingerprint, reservation, now, ttl);
                                        case SESSION_REVISION_CHANGED, EXPECTED_STATE_CHANGED ->
                                                Mono.error(UsernameRecoveryRequestException.revisionConflict());
                                        case SESSION_NOT_FOUND ->
                                                Mono.error(UsernameRecoveryRequestException.sessionAbsent());
                                        default -> Mono.error(UsernameRecoveryRequestException.systemFailure());
                                });
        }

        private boolean originRefererAllowed(String origin, String referer) {
                return allowedOrigins.isEmpty()
                                || originRefererValidator.validate(origin, referer, allowedOrigins).valid();
        }

        private Mono<UsernameRecoveryRequestResult> deliver(
                        AuthFlowSessionSnapshot reservedSnapshot,
                        UsernameRecoveryAuthFlowContext reserved,
                        UsernameRecoveryRequestCommand command,
                        UsernameRecoveryIdempotencyKey idempotencyKey,
                        UsernameRecoveryIdempotencyFingerprint fingerprint,
                        UsernameRecoveryIdempotencyReservation reservation,
                        Instant now, Duration ttl) {
                UsernameRecoverySessionCandidate candidate = findCandidate(reserved, command.schemeRef());
                return resolveEffectiveDestination(reserved)
                                .flatMap(effective -> invokeSender(reservedSnapshot, reserved, candidate,
                                                effective, command, idempotencyKey, fingerprint,
                                                reservation, now, ttl))
                                .onErrorResume(EffectiveDestinationPersistenceException.class,
                                                error -> persistDefiniteFailure(reservedSnapshot, reserved, command,
                                                                idempotencyKey, fingerprint, reservation, now, ttl));
        }

        private Mono<UsernameRecoveryEffectiveDestinationResult> resolveEffectiveDestination(
                        UsernameRecoveryAuthFlowContext context) {
                return effectiveDestinationPort.resolve(
                                new UsernameRecoveryEffectiveDestinationCommand(context.verifiedDestination()))
                                .onErrorMap(Exception.class, EffectiveDestinationPersistenceException::new);
        }

        private Mono<UsernameRecoveryRequestResult> invokeSender(
                        AuthFlowSessionSnapshot reservedSnapshot,
                        UsernameRecoveryAuthFlowContext context,
                        UsernameRecoverySessionCandidate candidate,
                        UsernameRecoveryEffectiveDestinationResult effective,
                        UsernameRecoveryRequestCommand command,
                        UsernameRecoveryIdempotencyKey idempotencyKey,
                        UsernameRecoveryIdempotencyFingerprint fingerprint,
                        UsernameRecoveryIdempotencyReservation reservation,
                        Instant now, Duration ttl) {
                String maskedDestination = maskOriginalDestination(context);
                MessageDeliveryCommand deliveryCommand = buildDeliveryCommand(
                                context, candidate, effective, reservation.operationReference().value());
                return messageDeliveryPort.deliver(deliveryCommand)
                                .onErrorMap(Exception.class, RequestPersistenceException::new)
                                .flatMap(result -> handleDeliveryOutcome(reservedSnapshot, context, command,
                                                idempotencyKey, fingerprint, reservation, now, ttl,
                                                effective, maskedDestination, result))
                                .onErrorResume(RequestPersistenceException.class,
                                                error -> persistUnknownOutcome(reservedSnapshot, context, command,
                                                                idempotencyKey, fingerprint, reservation, now, ttl));
        }

        private MessageDeliveryCommand buildDeliveryCommand(
                        UsernameRecoveryAuthFlowContext context,
                        UsernameRecoverySessionCandidate candidate,
                        UsernameRecoveryEffectiveDestinationResult effective,
                        String correlationId) {
                MessageDeliveryChannel channel = effective.channel()
                                == UsernameRecoveryVerifiedDestination.Channel.EMAIL
                                                ? MessageDeliveryChannel.EMAIL : MessageDeliveryChannel.SMS;
                boolean email = channel == MessageDeliveryChannel.EMAIL;
                return new MessageDeliveryCommand(
                                MessageDeliveryType.USERNAME_RECOVERY,
                                MessageDeliveryScenario.USERNAME_RECOVERY,
                                channel, effective.destination(),
                                email ? emailSubject : null,
                                email ? emailBody : smsBody,
                                null, Map.of("username", candidate.userName()),
                                context.language().toLanguageTag(), correlationId);
        }

        private Mono<UsernameRecoveryRequestResult> handleDeliveryOutcome(
                        AuthFlowSessionSnapshot reservedSnapshot,
                        UsernameRecoveryAuthFlowContext context,
                        UsernameRecoveryRequestCommand command,
                        UsernameRecoveryIdempotencyKey idempotencyKey,
                        UsernameRecoveryIdempotencyFingerprint fingerprint,
                        UsernameRecoveryIdempotencyReservation reservation,
                        Instant now, Duration ttl,
                        UsernameRecoveryEffectiveDestinationResult effective,
                        String maskedDestination,
                        MessageDeliveryResult result) {
                if (result == null || result.status() == null) {
                        return persistUnknownOutcome(reservedSnapshot, context, command,
                                        idempotencyKey, fingerprint, reservation, now, ttl);
                }
                return switch (result.status()) {
                        case ACCEPTED -> persistAccepted(reservedSnapshot, context, command,
                                        idempotencyKey, fingerprint, reservation, now, ttl,
                                        effective, maskedDestination, result);
                        case FAILED -> persistDefiniteFailure(reservedSnapshot, context, command,
                                        idempotencyKey, fingerprint, reservation, now, ttl);
                        case UNKNOWN_OUTCOME -> persistUnknownOutcome(reservedSnapshot, context, command,
                                        idempotencyKey, fingerprint, reservation, now, ttl);
                };
        }

        private Mono<UsernameRecoveryRequestResult> persistAccepted(
                        AuthFlowSessionSnapshot reservedSnapshot,
                        UsernameRecoveryAuthFlowContext context,
                        UsernameRecoveryRequestCommand command,
                        UsernameRecoveryIdempotencyKey idempotencyKey,
                        UsernameRecoveryIdempotencyFingerprint fingerprint,
                        UsernameRecoveryIdempotencyReservation reservation,
                        Instant now, Duration ttl,
                        UsernameRecoveryEffectiveDestinationResult effective,
                        String maskedDestination,
                        MessageDeliveryResult deliveryResult) {
                String senderReference = nonBlank(deliveryResult.providerMessageId())
                                ? deliveryResult.providerMessageId()
                                : reservation.operationReference().value();
                UsernameRecoveryAuthFlowContext requested =
                                context.markRequested(command.schemeRef(), senderReference, now);
                AuthFlowStatus status = requested.workflowStatus() == UsernameRecoveryWorkflowStatus.COMPLETED
                                ? AuthFlowStatus.COMPLETED : AuthFlowStatus.STARTED;
                return persistPostDelivery(reservedSnapshot, requested, status, now)
                                .flatMap(snapshot -> completeAccepted(snapshot, requested, command,
                                                idempotencyKey, fingerprint, reservation, now, ttl,
                                                effective, maskedDestination))
                                .onErrorResume(RequestPersistenceException.class,
                                                error -> recordUnknownAfterAcceptance(idempotencyKey, fingerprint,
                                                                reservation, now, ttl));
        }

        private Mono<AuthFlowSessionSnapshot> persistPostDelivery(
                        AuthFlowSessionSnapshot expected,
                        UsernameRecoveryAuthFlowContext context,
                        AuthFlowStatus status,
                        Instant now) {
                AuthFlowSession current = expected.session();
                AuthFlowSession base = new AuthFlowSession(
                                current.authFlowSessionId(), current.scenario(), status,
                                current.csrfTokenHash(), current.activeOtpChallengeId(), current.data(),
                                current.createdAt(), now, current.expiresAt());
                AuthFlowSession updated = contextBinding.bind(base, context, now);
                return authFlowSessionPort.updateRevisioned(
                                        new RevisionedAuthFlowSessionMutationCommand(expected, updated))
                                .onErrorMap(Exception.class, RequestPersistenceException::new)
                                .flatMap(result -> result.outcome() == AuthFlowSessionMutationOutcome.APPLIED
                                                ? Mono.just(result.snapshot())
                                                : Mono.error(new RequestPersistenceException(
                                                                new IllegalStateException(
                                                                                "post-delivery envelope CAS failed"))));
        }

        private Mono<UsernameRecoveryRequestResult> completeAccepted(
                        AuthFlowSessionSnapshot committed,
                        UsernameRecoveryAuthFlowContext requested,
                        UsernameRecoveryRequestCommand command,
                        UsernameRecoveryIdempotencyKey idempotencyKey,
                        UsernameRecoveryIdempotencyFingerprint fingerprint,
                        UsernameRecoveryIdempotencyReservation reservation,
                        Instant now, Duration ttl,
                        UsernameRecoveryEffectiveDestinationResult effective,
                        String maskedDestination) {
                ReplaySnapshot snapshot = UsernameRecoveryRequestReplayCodec.encode(
                                committed.session().authFlowSessionId());
                var complete = new UsernameRecoveryIdempotencyPort.CompleteCommand(
                                UsernameRecoveryIdempotencyAction.REQUEST, idempotencyKey, fingerprint,
                                reservation.operationReference(), snapshot, now, ttl);
                return idempotencyPort.complete(complete)
                                .onErrorMap(Exception.class, RequestPersistenceException::new)
                                .map(record -> new UsernameRecoveryRequestResult(
                                                UsernameRecoveryRequestResult.Outcome.REQUEST_ACCEPTED,
                                                requested.workflowStatus(), command.schemeRef(), maskedDestination,
                                                destinationType(effective.channel())))
                                .onErrorResume(RequestPersistenceException.class,
                                                error -> recordUnknownAfterAcceptance(idempotencyKey, fingerprint,
                                                                reservation, now, ttl));
        }

        private Mono<UsernameRecoveryRequestResult> persistDefiniteFailure(
                        AuthFlowSessionSnapshot reservedSnapshot,
                        UsernameRecoveryAuthFlowContext context,
                        UsernameRecoveryRequestCommand command,
                        UsernameRecoveryIdempotencyKey idempotencyKey,
                        UsernameRecoveryIdempotencyFingerprint fingerprint,
                        UsernameRecoveryIdempotencyReservation reservation,
                        Instant now, Duration ttl) {
                UsernameRecoveryAuthFlowContext failed = context.markFailed(command.schemeRef());
                return persistPostDelivery(reservedSnapshot, failed, AuthFlowStatus.STARTED, now)
                                .flatMap(snapshot -> completeDefiniteFailure(snapshot, idempotencyKey,
                                                fingerprint, reservation, now, ttl))
                                .onErrorResume(RequestPersistenceException.class,
                                                error -> recordUnknownAfterAcceptance(idempotencyKey, fingerprint,
                                                                reservation, now, ttl));
        }

        private Mono<UsernameRecoveryRequestResult> completeDefiniteFailure(
                        AuthFlowSessionSnapshot snapshot,
                        UsernameRecoveryIdempotencyKey idempotencyKey,
                        UsernameRecoveryIdempotencyFingerprint fingerprint,
                        UsernameRecoveryIdempotencyReservation reservation,
                        Instant now, Duration ttl) {
                var complete = new UsernameRecoveryIdempotencyPort.CompleteCommand(
                                UsernameRecoveryIdempotencyAction.REQUEST, idempotencyKey, fingerprint,
                                reservation.operationReference(),
                                UsernameRecoveryRequestReplayCodec.encode(snapshot.session().authFlowSessionId()),
                                now, ttl);
                return idempotencyPort.complete(complete)
                                .onErrorMap(Exception.class, RequestPersistenceException::new)
                                .map(record -> new UsernameRecoveryRequestResult(
                                                UsernameRecoveryRequestResult.Outcome.SYSTEM_FAILED,
                                                null, null, null, null));
        }

        private Mono<UsernameRecoveryRequestResult> persistUnknownOutcome(
                        AuthFlowSessionSnapshot reservedSnapshot,
                        UsernameRecoveryAuthFlowContext context,
                        UsernameRecoveryRequestCommand command,
                        UsernameRecoveryIdempotencyKey idempotencyKey,
                        UsernameRecoveryIdempotencyFingerprint fingerprint,
                        UsernameRecoveryIdempotencyReservation reservation,
                        Instant now, Duration ttl) {
                UsernameRecoveryAuthFlowContext unknown = context.markUnknownOutcome(command.schemeRef());
                return persistPostDelivery(reservedSnapshot, unknown,
                                AuthFlowStatus.UNKNOWN_OUTCOME_BLOCKED, now)
                                .onErrorResume(RequestPersistenceException.class, error -> Mono.empty())
                                .then(recordUnknownAfterAcceptance(idempotencyKey, fingerprint,
                                                reservation, now, ttl));
        }

        private Mono<UsernameRecoveryRequestResult> recordUnknownAfterAcceptance(
                        UsernameRecoveryIdempotencyKey idempotencyKey,
                        UsernameRecoveryIdempotencyFingerprint fingerprint,
                        UsernameRecoveryIdempotencyReservation reservation,
                        Instant now, Duration ttl) {
                var command = new UsernameRecoveryIdempotencyPort.UnknownOutcomeCommand(
                                UsernameRecoveryIdempotencyAction.REQUEST, idempotencyKey, fingerprint,
                                reservation.operationReference(), now, ttl);
                return idempotencyPort.recordUnknownOutcome(command)
                                .onErrorMap(Exception.class, RequestPersistenceException::new)
                                .map(record -> new UsernameRecoveryRequestResult(
                                                UsernameRecoveryRequestResult.Outcome.UNKNOWN_OUTCOME,
                                                null, null, null, null));
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
                AuthFlowSession session = envelope.session();
                if (!session.authFlowSessionId().equals(command.authFlowSessionId())
                                || session.scenario() != AuthFlowScenario.USERNAME_RECOVERY
                                || session.isExpired(now)
                                || (session.status() != AuthFlowStatus.STARTED
                                                && session.status() != AuthFlowStatus.COMPLETED
                                                && session.status() != AuthFlowStatus.UNKNOWN_OUTCOME_BLOCKED)) {
                        return Mono.error(UsernameRecoveryRequestException.systemFailure());
                }
                final UsernameRecoveryAuthFlowContext context;
                try {
                        context = contextBinding.load(session);
                } catch (RuntimeException error) {
                        return Mono.error(UsernameRecoveryRequestException.systemFailure(error));
                }
                if (!replayStatusesConsistent(session.status(), context.workflowStatus())) {
                        return Mono.error(UsernameRecoveryRequestException.systemFailure());
                }
                UsernameRecoverySessionCandidate candidate = findCandidate(context, command.schemeRef());
                if (candidate == null || !candidate.registered()) {
                        return Mono.error(UsernameRecoveryRequestException.systemFailure());
                }
                return switch (candidate.deliveryState()) {
                        case REQUESTED -> Mono.just(new UsernameRecoveryRequestResult(
                                        UsernameRecoveryRequestResult.Outcome.REQUEST_ACCEPTED,
                                        context.workflowStatus(), command.schemeRef(),
                                        maskOriginalDestination(context),
                                        destinationType(context.verifiedDestination().channel())));
                        case FAILED -> Mono.just(new UsernameRecoveryRequestResult(
                                        UsernameRecoveryRequestResult.Outcome.SYSTEM_FAILED,
                                        null, null, null, null));
                        case UNKNOWN_OUTCOME -> Mono.just(new UsernameRecoveryRequestResult(
                                        UsernameRecoveryRequestResult.Outcome.UNKNOWN_OUTCOME,
                                        null, null, null, null));
                        default -> Mono.error(UsernameRecoveryRequestException.systemFailure());
                };
        }

        private boolean replayStatusesConsistent(
                        AuthFlowStatus envelopeStatus,
                        UsernameRecoveryWorkflowStatus workflowStatus) {
                if (workflowStatus == UsernameRecoveryWorkflowStatus.COMPLETED) {
                        return envelopeStatus == AuthFlowStatus.COMPLETED;
                }
                return envelopeStatus == AuthFlowStatus.STARTED
                                || envelopeStatus == AuthFlowStatus.UNKNOWN_OUTCOME_BLOCKED;
        }

        private String maskOriginalDestination(UsernameRecoveryAuthFlowContext context) {
                var original = context.verifiedDestination();
                return destinationMasker.mask(original.channel(), original.value());
        }

        private UsernameRecoverySessionCandidate findCandidate(
                        UsernameRecoveryAuthFlowContext context, UsernameRecoverySchemeRef schemeRef) {
                return context.candidates().stream()
                                .filter(candidate -> candidate.schemeRef().equals(schemeRef))
                                .findFirst().orElse(null);
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
