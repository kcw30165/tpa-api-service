package com.bct.ngtpa.apiservice.application.usecase.auth.login.otp.verify;

import static org.assertj.core.api.Assertions.assertThat;
import com.bct.ngtpa.apiservice.application.dto.auth.otp.*;
import com.bct.ngtpa.apiservice.application.dto.session.*;
import com.bct.ngtpa.apiservice.application.port.out.*;
import com.bct.ngtpa.apiservice.application.usecase.auth.login.otp.*;
import java.time.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class LoginOtpVerifyServiceTest {
    static final VerifyLoginOtpCommand C = new VerifyLoginOtpCommand("afs_opaque", "csrf", "123456", "idem_opaque",
            "https://portal", null, "en", "req");

    @Test
    void invalidKeepsFlowAndReturnsRemainingAttempts() {
        var f = new Fixture(OtpVerificationOutcome.INVALID, 3);
        StepVerifier.create(f.service.verify(C)).assertNext(r -> {
            assertThat(r.status()).isEqualTo(VerifyLoginOtpStatus.INVALID);
            assertThat(r.remainingVerifyAttempts()).isEqualTo(3);
            assertThat(r.clearAuthFlowCookie()).isFalse();
        }).verifyComplete();
        assertThat(f.cancelled.get()).isZero();
    }

    @Test
    void limitReachedCancelsPreauthAndRequiresRestart() {
        var f = new Fixture(OtpVerificationOutcome.LOCKED, 0);
        StepVerifier.create(f.service.verify(C)).assertNext(r -> {
            assertThat(r.status()).isEqualTo(VerifyLoginOtpStatus.VERIFY_ATTEMPTS_EXCEEDED);
            assertThat(r.clearAuthFlowCookie()).isTrue();
        }).verifyComplete();
        assertThat(f.cancelled.get()).isOne();
    }

    @Test
    void validMarksOtpVerifiedAndContinuesToAccounts() {
        var f = new Fixture(OtpVerificationOutcome.VALID, 4);
        StepVerifier.create(f.service.verify(C)).assertNext(r -> {
            assertThat(r.status()).isEqualTo(VerifyLoginOtpStatus.ACCOUNTS_READY);
            assertThat(r.accounts()).hasSize(1);
            assertThat(r.accounts().getFirst().accountRef().value()).isEqualTo("acc_abcdefghijklmnopqrstuvwx");
        }).verifyComplete();
        assertThat(f.statusUpdates.get()).isOne();
    }

    static final class Fixture {
        final AtomicInteger cancelled = new AtomicInteger(), statusUpdates = new AtomicInteger();
        final LoginOtpVerifyService service;

        Fixture(OtpVerificationOutcome outcome, int remaining) {
            LoginProtectedAuthFlowContextLoader loader = c -> Mono.just(LoginProtectedAuthFlowContextResult.valid(
                    new com.bct.ngtpa.apiservice.application.dto.auth.login.preauth.LoginPreAuthContext("member", "MBR",
                            com.bct.ngtpa.apiservice.application.dto.ActorUserRole.ROLE_USER_MBR.name(), java.util.Map.of(), java.time.Instant.parse("2026-07-20T00:00:00Z"), "v1"),
                    "otp_active", null, "csrf_hash"));
            OtpChallengePort otp = new StubOtp(outcome, remaining);
            AuthFlowSessionCancellationPort cancel = (id, reason, now) -> {
                cancelled.incrementAndGet();
                return Mono.just(new SessionCancellationResult(SessionCancellationStatus.CANCELLED));
            };
            LoginOtpVerifiedSessionPort verified = (session, correlation) -> {
                statusUpdates.incrementAndGet();
                return Mono.empty();
            };
            service = LoginOtpVerifyTestSupport.newService(loader, otp, verified, cancel,
                    new com.bct.ngtpa.apiservice.application.usecase.auth.login.account.GetLoginAccountsUseCase(
                            c -> Mono.just(
                                    com.bct.ngtpa.apiservice.application.dto.auth.login.account.LoginAccountAuthorizationResult
                                            .available(java.util.List.of(
                                                    new com.bct.ngtpa.apiservice.application.dto.auth.login.account.LoginAccountRecord(
                                                            "DB", "TR", "12", "OE", 1, null, null))))),
                    new com.bct.ngtpa.apiservice.application.usecase.auth.login.account.LoginAccountRecordValidationService(
                            11, 4, '0'),
                    new com.bct.ngtpa.apiservice.application.usecase.auth.login.account.LoginAccountReferenceAssignmentService(
                            () -> new com.bct.ngtpa.apiservice.application.dto.auth.login.account.LoginAccountRef(
                                    "acc_abcdefghijklmnopqrstuvwx"),
                            8),
                    command -> reactor.core.publisher.Mono.just(
                            com.bct.ngtpa.apiservice.application.usecase.auth.login.session.EstablishAuthenticatedLoginSessionResult
                                    .established(
                                            new com.bct.ngtpa.apiservice.application.dto.session.authenticated.AuthenticatedSessionId(
                                                    "sid_abcdefghijklmnopqrstuvwx"),
                                            "authenticated_csrf_token")),
                    Clock.systemUTC());
        }
    }

    static final class StubOtp implements OtpChallengePort {
        final OtpVerificationOutcome o;
        final int r;

        StubOtp(OtpVerificationOutcome o, int r) {
            this.o = o;
            this.r = r;
        }

        public Mono<VerifyOtpChallengeResult> verifyChallenge(VerifyOtpChallengeCommand c) {
            return Mono.just(new VerifyOtpChallengeResult(c.otpChallengeId(), o, r, null));
        }

        public Mono<CreateOtpChallengeResult> createChallenge(CreateOtpChallengeCommand c) {
            return Mono.error(new UnsupportedOperationException());
        }

        public Mono<ReplaceOtpChallengeResult> replaceChallenge(ReplaceOtpChallengeCommand c) {
            return Mono.error(new UnsupportedOperationException());
        }

        public Mono<Void> invalidateChallenge(InvalidateOtpChallengeCommand c) {
            return Mono.empty();
        }
    }
}
