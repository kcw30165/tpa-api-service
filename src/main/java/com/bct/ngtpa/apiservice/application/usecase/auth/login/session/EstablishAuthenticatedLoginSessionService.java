package com.bct.ngtpa.apiservice.application.usecase.auth.login.session;

import com.bct.ngtpa.apiservice.application.port.in.EstablishAuthenticatedLoginSessionUseCase;
import com.bct.ngtpa.apiservice.application.dto.auth.login.handoff.*;
import com.bct.ngtpa.apiservice.application.port.out.AuthenticatedLoginHandoffPort;
import com.bct.ngtpa.apiservice.application.usecase.session.authenticated.*;
import com.bct.ngtpa.apiservice.application.usecase.session.authflow.CsrfTokenGenerator;
import com.bct.ngtpa.apiservice.application.usecase.session.authflow.CsrfTokenHasher;

import java.time.Duration;
import java.time.Instant;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Objects;
import reactor.core.publisher.Mono;

public final class EstablishAuthenticatedLoginSessionService
        implements EstablishAuthenticatedLoginSessionUseCase {
    private final CreateAuthenticatedSessionUseCase create;
    private final DeleteAuthFlowSessionPort cleanup;
    private final RevokeAuthenticatedSessionUseCase revoke;
    private final CsrfTokenGenerator csrfTokenGenerator;
    private final CsrfTokenHasher csrfTokenHasher;
    private final AuthenticatedLoginHandoffPort handoffs;
    private final AuthenticatedSessionIdGenerator authenticatedSessionIds;
    private final Duration handoffRetention;

    public EstablishAuthenticatedLoginSessionService(
            CreateAuthenticatedSessionUseCase create,
            DeleteAuthFlowSessionPort cleanup,
            RevokeAuthenticatedSessionUseCase revoke,
            CsrfTokenGenerator csrfTokenGenerator,
            CsrfTokenHasher csrfTokenHasher,
            AuthenticatedLoginHandoffPort handoffs,
            AuthenticatedSessionIdGenerator authenticatedSessionIds,
            Duration handoffRetention) {
        this.create = Objects.requireNonNull(create);
        this.cleanup = Objects.requireNonNull(cleanup);
        this.revoke = Objects.requireNonNull(revoke);
        this.csrfTokenGenerator = Objects.requireNonNull(csrfTokenGenerator);
        this.csrfTokenHasher = Objects.requireNonNull(csrfTokenHasher);
        this.handoffs = Objects.requireNonNull(handoffs);
        this.authenticatedSessionIds = Objects.requireNonNull(authenticatedSessionIds);
        this.handoffRetention = Objects.requireNonNull(handoffRetention);
    }
    public EstablishAuthenticatedLoginSessionService(
            CreateAuthenticatedSessionUseCase create,
            DeleteAuthFlowSessionPort cleanup,
            RevokeAuthenticatedSessionUseCase revoke,
            CsrfTokenGenerator csrfTokenGenerator,
            CsrfTokenHasher csrfTokenHasher) {
        this(create, cleanup, revoke, csrfTokenGenerator, csrfTokenHasher,
                new UnsupportedCompatibilityHandoffPort(),
                new SecureAuthenticatedSessionIdGenerator(),
                Duration.ofMinutes(15));
    }

    @Override
    public Mono<EstablishAuthenticatedLoginSessionResult> establish(
            EstablishAuthenticatedLoginSessionCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        return Mono.defer(() -> {
            String rawAuthenticatedCsrfToken = csrfTokenGenerator.generate();
            if (rawAuthenticatedCsrfToken == null || rawAuthenticatedCsrfToken.isBlank()) {
                return Mono.just(EstablishAuthenticatedLoginSessionResult.failed(
                        EstablishAuthenticatedLoginSessionStatus.SESSION_CREATION_FAILED));
            }
            String authenticatedCsrfHash = csrfTokenHasher.hash(rawAuthenticatedCsrfToken);
            var proposedSessionId = authenticatedSessionIds.generate();
            var proof = command.consumptionProof();
            return handoffs.createIfAbsent(new CreateAuthenticatedLoginHandoffCommand(
                            proof, proposedSessionId, proof.committedAt().plus(handoffRetention)))
                    .flatMap(reserved -> {
                        if (reserved.outcome() != AuthenticatedLoginHandoffCreateOutcome.CREATED
                                && reserved.outcome() != AuthenticatedLoginHandoffCreateOutcome.EXISTING) {
                            return Mono.just(EstablishAuthenticatedLoginSessionResult.failed(
                                    EstablishAuthenticatedLoginSessionStatus.SESSION_CREATION_FAILED));
                        }
                        var reservedSessionId = reserved.handoff().authenticatedSessionId();
                        var createCommand = new CreateAuthenticatedSessionCommand(
                                command.actorContext(),
                                LoginAuthenticatedSessionAccountMapper.map(command.accounts()),
                                authenticatedCsrfHash,
                                command.correlationId(),
                                reservedSessionId);
                        return create.create(createCommand)
                    .flatMap(created -> {
                        if (created.status() != AuthenticatedSessionLifecycleStatus.ACTIVE
                                || created.session() == null) {
                            return Mono.just(EstablishAuthenticatedLoginSessionResult.failed(
                                    EstablishAuthenticatedLoginSessionStatus.SESSION_CREATION_FAILED));
                        }
                        var id = created.session().authenticatedSessionId();
                        return handoffs.recordAuthenticatedSessionCommitted(
                                        new RecordAuthenticatedSessionCommittedCommand(
                                                command.handoffReference(),
                                                command.operationReference(),
                                                command.reservationHash(),
                                                id))
                                .flatMap(committed -> cleanup.delete(command.authFlowSessionId())
                                        .then(Mono.defer(() -> handoffs.recordCleanupProof(
                                                new RecordAuthFlowCleanupProofCommand(
                                                        command.handoffReference(),
                                                        id,
                                                        generateCleanupProof(),
                                                        Instant.now()))))
                                        .thenReturn(EstablishAuthenticatedLoginSessionResult.established(
                                                id, rawAuthenticatedCsrfToken)))
                                .onErrorResume(error -> revoke.revoke(
                                                new RevokeActiveAuthenticatedSessionCommand(
                                                        id,
                                                        "PRE_AUTH_CLEANUP_FAILED",
                                                        command.correlationId()))
                                        .onErrorResume(ignored -> Mono.just(
                                                AuthenticatedSessionLifecycleResult.of(
                                                        AuthenticatedSessionLifecycleStatus.FAILED)))
                                        .thenReturn(EstablishAuthenticatedLoginSessionResult.failed(
                                                EstablishAuthenticatedLoginSessionStatus.CLEANUP_FAILED)));
                    });
                    });
        }).onErrorReturn(EstablishAuthenticatedLoginSessionResult.failed(
                EstablishAuthenticatedLoginSessionStatus.SESSION_CREATION_FAILED));
    }


    private static String generateCleanupProof() {
        byte[] value = new byte[32];
        new SecureRandom().nextBytes(value);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }

    private static final class UnsupportedCompatibilityHandoffPort implements AuthenticatedLoginHandoffPort {
        public Mono<AuthenticatedLoginHandoffCreateResult> createIfAbsent(CreateAuthenticatedLoginHandoffCommand command) {
            var handoff = new AuthenticatedLoginHandoff(command.proof().handoffReference(),
                    command.proof().operationReference(), command.proof().reservationHash(),
                    AuthenticatedLoginHandoffStatus.RESERVED, command.proposedAuthenticatedSessionId(), null,
                    command.proof().committedAt(), command.expiresAt());
            return Mono.just(new AuthenticatedLoginHandoffCreateResult(
                    AuthenticatedLoginHandoffCreateOutcome.CREATED, handoff));
        }
        public Mono<AuthenticatedLoginHandoff> loadByHandoff(com.bct.ngtpa.apiservice.application.dto.auth.login.otp.verify.AuthenticatedLoginHandoffReference reference) { return Mono.empty(); }
        public Mono<AuthenticatedLoginHandoff> recordAuthenticatedSessionCommitted(
                RecordAuthenticatedSessionCommittedCommand command) {
            return Mono.just(new AuthenticatedLoginHandoff(command.handoffReference(),
                    command.operationReference(), command.reservationHash(),
                    AuthenticatedLoginHandoffStatus.AUTHENTICATED_SESSION_COMMITTED,
                    command.authenticatedSessionId(), null, Instant.EPOCH, Instant.EPOCH.plusSeconds(900)));
        }
        public Mono<AuthenticatedLoginHandoff> recordCleanupProof(RecordAuthFlowCleanupProofCommand command) {
            return Mono.just(new AuthenticatedLoginHandoff(command.handoffReference(),
                    new com.bct.ngtpa.apiservice.application.dto.auth.login.otp.verify.LoginOtpVerificationOperationReference(
                            "00000000000000000000000000000000"),
                    "legacy-test-boundary", AuthenticatedLoginHandoffStatus.AUTH_FLOW_DELETED,
                    command.authenticatedSessionId(), command.cleanupProof(),
                    Instant.EPOCH, Instant.EPOCH.plusSeconds(900)));
        }
    }
}
