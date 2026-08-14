package com.bct.ngtpa.apiservice.application.usecase.auth.login.otp.verify;

import static org.assertj.core.api.Assertions.assertThat;
import com.bct.ngtpa.apiservice.application.dto.auth.login.account.*;
import com.bct.ngtpa.apiservice.application.dto.auth.login.preauth.LoginPreAuthContext;
import com.bct.ngtpa.apiservice.application.dto.auth.otp.*;
import com.bct.ngtpa.apiservice.application.dto.session.*;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.*;
import com.bct.ngtpa.apiservice.application.port.out.*;
import com.bct.ngtpa.apiservice.application.usecase.auth.login.account.*;
import com.bct.ngtpa.apiservice.application.usecase.auth.login.otp.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class LoginOtpSuccessAccountContinuationTest {
    static final VerifyLoginOtpCommand C = new VerifyLoginOtpCommand("afs_opaque", "csrf", "123456", "idem_opaque",
            "https://portal", null, "en", "req");

    @Test
    void validOtpLoadsValidatesAndAssignsOrderedOpaqueAccountRefs() {
        var f = new Fixture(LoginAccountAuthorizationResult.available(List.of(record("12", 1), record("34", 2))));
        StepVerifier.create(f.service.verify(C)).assertNext(r -> {
            assertThat(r.status()).isEqualTo(VerifyLoginOtpStatus.ACCOUNTS_READY);
            assertThat(r.accounts()).extracting(a -> a.accountRef().value())
                    .containsExactly("acc_abcdefghijklmnopqrstuvwx", "acc_yxwvutsrqponmlkjihgfedcb");
            assertThat(r.accounts()).allSatisfy(a -> assertThat(a.toString()).doesNotContain("12", "34"));
        }).verifyComplete();
        assertThat(f.verified.get()).isOne();
    }

    @Test
    void unavailableAccountListReturnsStableOutcome() {
        var f = new Fixture(LoginAccountAuthorizationResult.unavailable());
        StepVerifier.create(f.service.verify(C))
                .assertNext(r -> assertThat(r.status()).isEqualTo(VerifyLoginOtpStatus.ACCOUNT_LIST_UNAVAILABLE))
                .verifyComplete();
    }

    @Test
    void returnedButZeroValidAccountsIsDistinct() {
        var f = new Fixture(LoginAccountAuthorizationResult.available(List.of(record("0", 0))));
        StepVerifier.create(f.service.verify(C))
                .assertNext(r -> assertThat(r.status()).isEqualTo(VerifyLoginOtpStatus.ZERO_VALID_ACCOUNTS))
                .verifyComplete();
    }

    static LoginAccountRecord record(String policy, int cert) {
        return new LoginAccountRecord("DB", "TR", policy, "OE", cert, null, null);
    }

    static final class Fixture {
        final AtomicInteger verified = new AtomicInteger();
        final LoginOtpVerifyService service;

        Fixture(LoginAccountAuthorizationResult authorization) {
            LoginPreAuthContext context = new LoginPreAuthContext("member", "MBR", com.bct.ngtpa.apiservice.application.dto.ActorUserRole.ROLE_USER_MBR.name(), Map.of(),
                    Instant.parse("2026-07-20T00:00:00Z"), "v1");
            LoginProtectedAuthFlowContextLoader loader = c -> Mono
                    .just(LoginProtectedAuthFlowContextResult.valid(context, "otp_active", null, "csrf_hash"));
            OtpChallengePort otp = new OtpChallengePort() {
                public Mono<VerifyOtpChallengeResult> verifyChallenge(VerifyOtpChallengeCommand c) {
                    return Mono.just(
                            new VerifyOtpChallengeResult(c.otpChallengeId(), OtpVerificationOutcome.VALID, 4, null));
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
            };
            LoginOtpVerifiedSessionPort verifiedPort = (id, req) -> {
                verified.incrementAndGet();
                return Mono.empty();
            };
            AuthFlowSessionCancellationPort cancel = (id, reason, now) -> Mono
                    .just(new SessionCancellationResult(SessionCancellationStatus.CANCELLED));
            GetLoginAccountsUseCase accounts = new GetLoginAccountsUseCase(c -> Mono.just(authorization));
            LoginAccountRecordValidationService validation = new LoginAccountRecordValidationService(11, 4, '0');
            Iterator<String> refs = List.of("acc_abcdefghijklmnopqrstuvwx", "acc_yxwvutsrqponmlkjihgfedcb").iterator();
            LoginAccountReferenceAssignmentService assignment = new LoginAccountReferenceAssignmentService(
                    () -> new LoginAccountRef(refs.next()), 8);
            service = LoginOtpVerifyTestSupport.newService(loader, otp, verifiedPort, cancel, accounts, validation,
                    assignment,
                    command -> reactor.core.publisher.Mono.just(
                            com.bct.ngtpa.apiservice.application.usecase.auth.login.session.EstablishAuthenticatedLoginSessionResult
                                    .established(
                                            new com.bct.ngtpa.apiservice.application.dto.session.authenticated.AuthenticatedSessionId(
                                                    "sid_abcdefghijklmnopqrstuvwx"),
                                            "authenticated_csrf_token")),
                    Clock.fixed(Instant.parse("2026-07-20T00:00:00Z"), ZoneOffset.UTC));
        }
    }
}
