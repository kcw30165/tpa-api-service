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
        LoginOtpVerifyPersistencePort persistence = command -> legacyOtp.verifyChallenge(
                new VerifyOtpChallengeCommand(command.expectedChallengeId(),
                        command.submittedOtp().revealForComparison(), command.verificationTime()))
                .map(LoginOtpVerifyTestSupport::map);
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

    private static LoginOtpVerifyPersistenceResult map(VerifyOtpChallengeResult result) {
        LoginOtpVerifyPersistenceOutcome outcome = switch (result.outcome()) {
            case VALID -> LoginOtpVerifyPersistenceOutcome.VERIFIED_AND_CONSUMED;
            case INVALID -> LoginOtpVerifyPersistenceOutcome.INVALID;
            case EXPIRED -> LoginOtpVerifyPersistenceOutcome.EXPIRED;
            case LOCKED -> LoginOtpVerifyPersistenceOutcome.LOCKED;
            case ALREADY_VERIFIED -> LoginOtpVerifyPersistenceOutcome.ALREADY_COMMITTED;
            case NOT_FOUND -> LoginOtpVerifyPersistenceOutcome.CHALLENGE_NOT_FOUND;
            case INVALIDATED -> LoginOtpVerifyPersistenceOutcome.EXPECTED_STATE_CHANGED;
        };
        return new LoginOtpVerifyPersistenceResult(outcome, result.remainingAttempts(), null);
    }

    private static AuthFlowSession session(AuthFlowSessionId id) {
        Instant now = Instant.parse("2026-07-20T00:00:00Z");
        return new AuthFlowSession(id, AuthFlowScenario.LOGIN, AuthFlowStatus.OTP_SENT,
                "csrf_hash", "otp_active", AuthFlowSessionData.empty(), now, now,
                now.plus(Duration.ofMinutes(15)));
    }
}
