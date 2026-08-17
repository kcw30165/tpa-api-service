package com.bct.ngtpa.apiservice.application.usecase.auth.login.otp.verify;

import com.bct.ngtpa.apiservice.application.dto.auth.login.otp.verify.*;
import com.bct.ngtpa.apiservice.application.dto.auth.otp.*;
import com.bct.ngtpa.apiservice.application.dto.session.SessionLockHandle;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.*;
import com.bct.ngtpa.apiservice.application.port.in.EstablishAuthenticatedLoginSessionUseCase;
import com.bct.ngtpa.apiservice.application.port.out.*;
import com.bct.ngtpa.apiservice.application.service.auth.login.otp.verify.*;
import com.bct.ngtpa.apiservice.application.usecase.auth.login.account.*;
import java.time.*;
import java.util.Optional;
import reactor.core.publisher.Mono;

final class LoginOtpVerifyTestSupport {
    private LoginOtpVerifyTestSupport() {
    }

    static LoginOtpVerifyService newService(LoginProtectedAuthFlowContextLoader loader,
            OtpChallengePort legacyOtp, LoginOtpVerifiedSessionPort verified,
            AuthFlowSessionCancellationPort cancellation, GetLoginAccountsUseCase accounts,
            LoginAccountRecordValidationService validation,
            LoginAccountReferenceAssignmentService assignment,
            EstablishAuthenticatedLoginSessionUseCase transition, Clock clock) {
        LoginOtpVerifyPersistencePort persistence = new LoginOtpVerifyPersistencePort() {
            @Override
            public reactor.core.publisher.Mono<com.bct.ngtpa.apiservice.application.dto.auth.login.otp.verify.LoginOtpVerifyReservationResult> reserveOrLoad(ReserveLoginOtpVerifyCommand command) {
                var reservation = new com.bct.ngtpa.apiservice.application.dto.auth.login.otp.verify.LoginOtpVerifyReservation(
                        command.requestHash(), com.bct.ngtpa.apiservice.application.dto.auth.login.otp.verify.LoginOtpVerifyReservationStatus.PROCESSING,
                        command.proposedHandoff(), command.proposedOperation(), null, null, null, command.now(), command.expiresAt());
                return reactor.core.publisher.Mono.just(new com.bct.ngtpa.apiservice.application.dto.auth.login.otp.verify.LoginOtpVerifyReservationResult(
                        com.bct.ngtpa.apiservice.application.dto.auth.login.otp.verify.LoginOtpVerifyReservationOutcome.STARTED, reservation));
            }

            @Override
            public reactor.core.publisher.Mono<com.bct.ngtpa.apiservice.application.dto.auth.login.otp.verify.LoginOtpVerifyPersistenceResult> verify(RevisionedLoginOtpVerifyCommand command) {
                return legacyOtp.verifyChallenge(new VerifyOtpChallengeCommand(command.expectedChallengeId(),
                        command.submittedOtp().revealForComparison(), command.verificationTime()))
                        .map(result -> map(command, result));
            }

            @Override
            public reactor.core.publisher.Mono<com.bct.ngtpa.apiservice.application.dto.auth.login.otp.verify.LoginOtpVerifyReservation> recordVerificationCommitted(RecordLoginOtpVerifyCommittedCommand command) {
                var reservation = new com.bct.ngtpa.apiservice.application.dto.auth.login.otp.verify.LoginOtpVerifyReservation(
                        command.requestHash(), com.bct.ngtpa.apiservice.application.dto.auth.login.otp.verify.LoginOtpVerifyReservationStatus.VERIFICATION_COMMITTED,
                        command.proof().handoffReference(), command.proof().operationReference(), command.proof(), null, null, command.proof().committedAt(), command.proof().committedAt().plusSeconds(600));
                return reactor.core.publisher.Mono.just(reservation);
            }

            @Override
            public reactor.core.publisher.Mono<com.bct.ngtpa.apiservice.application.dto.auth.login.otp.verify.LoginOtpVerifyReservation> recordConclusiveFailure(com.bct.ngtpa.apiservice.application.dto.auth.login.idempotency.LoginIdempotencyKey key, String requestHash,
                    com.bct.ngtpa.apiservice.application.dto.auth.login.otp.verify.LoginOtpVerifyPersistenceOutcome outcome, Integer remainingAttempts) {
                var now = java.time.Instant.now();
                var reservation = new com.bct.ngtpa.apiservice.application.dto.auth.login.otp.verify.LoginOtpVerifyReservation(
                        requestHash, com.bct.ngtpa.apiservice.application.dto.auth.login.otp.verify.LoginOtpVerifyReservationStatus.PROCESSING,
                        null, null, null, null, null, now, now.plusSeconds(600));
                return reactor.core.publisher.Mono.just(reservation);
            }
        };
        AuthFlowSessionMutationLockPort lock = new AuthFlowSessionMutationLockPort() {
            public Mono<SessionLockHandle> acquire(AuthFlowSessionId id, Duration lease) {
                return Mono.just(new SessionLockHandle(true, "test-lock", "test-owner"));
            }

            public Mono<Boolean> release(SessionLockHandle handle) {
                return Mono.just(true);
            }
        };
        AuthFlowSessionPort sessions = new AuthFlowSessionPort() {
            public Mono<AuthFlowSession> create(AuthFlowSessionCreateCommand command) {
                return Mono.error(new UnsupportedOperationException());
            }

            public Mono<Optional<AuthFlowSession>> load(AuthFlowSessionId id) {
                return Mono.just(Optional.of(session(id)));
            }

            public Mono<Optional<AuthFlowSessionSnapshot>> loadSnapshot(AuthFlowSessionId id) {
                return Mono.just(Optional.of(new AuthFlowSessionSnapshot(session(id),
                        new SessionRevision("11111111111111111111111111111111"))));
            }
        };
        return new LoginOtpVerifyService(loader, persistence, lock, sessions,
                () -> new SessionRevision("22222222222222222222222222222222"),
                new SecureLoginOtpVerifyReferenceGenerator(), new LoginOtpVerifyRequestHasher(),
                Duration.ofSeconds(30), verified, cancellation, accounts, validation, assignment,
                transition, clock);
    }

    private static LoginOtpVerifyPersistenceResult map(
            RevisionedLoginOtpVerifyCommand command,
            VerifyOtpChallengeResult result) {
        LoginOtpVerifyPersistenceOutcome outcome = switch (result.outcome()) {
            case VALID -> LoginOtpVerifyPersistenceOutcome.VERIFIED_AND_CONSUMED;
            case INVALID -> LoginOtpVerifyPersistenceOutcome.INVALID;
            case EXPIRED -> LoginOtpVerifyPersistenceOutcome.EXPIRED;
            case LOCKED -> LoginOtpVerifyPersistenceOutcome.LOCKED;
            case ALREADY_VERIFIED -> LoginOtpVerifyPersistenceOutcome.ALREADY_COMMITTED;
            case NOT_FOUND -> LoginOtpVerifyPersistenceOutcome.CHALLENGE_NOT_FOUND;
            case INVALIDATED -> LoginOtpVerifyPersistenceOutcome.EXPECTED_STATE_CHANGED;
        };
        LoginOtpConsumptionProof proof = result.outcome() == OtpVerificationOutcome.VALID
                ? new LoginOtpConsumptionProof(
                        command.reservationHash(),
                        command.handoffReference(),
                        command.operationReference(),
                        "committed",
                        command.verificationTime())
                : null;
        return new LoginOtpVerifyPersistenceResult(outcome, result.remainingAttempts(), proof);
    }

    private static AuthFlowSession session(AuthFlowSessionId id) {
        Instant now = Instant.parse("2026-07-20T00:00:00Z");
        return new AuthFlowSession(id, AuthFlowScenario.LOGIN, AuthFlowStatus.OTP_SENT,
                "csrf_hash", "otp_active", AuthFlowSessionData.empty(), now, now,
                now.plus(Duration.ofMinutes(15)));
    }
}
