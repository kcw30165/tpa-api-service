package com.bct.ngtpa.apiservice.application.usecase.auth.login.otp.verify;

import com.bct.ngtpa.apiservice.application.dto.ActorContext;
import com.bct.ngtpa.apiservice.application.dto.auth.login.account.*;
import com.bct.ngtpa.apiservice.application.dto.auth.login.otp.verify.*;
import com.bct.ngtpa.apiservice.application.service.auth.login.otp.verify.*;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.*;
import com.bct.ngtpa.apiservice.application.port.in.*;
import com.bct.ngtpa.apiservice.application.port.out.*;
import com.bct.ngtpa.apiservice.application.usecase.auth.login.account.*;
import com.bct.ngtpa.apiservice.application.usecase.auth.login.otp.*;
import com.bct.ngtpa.apiservice.application.usecase.auth.login.session.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.time.*;
import java.util.*;
import reactor.core.publisher.Mono;

public final class LoginOtpVerifyService implements VerifyLoginOtpUseCase {
        private static final Logger LOG = LoggerFactory.getLogger(LoginOtpVerifyService.class);
        private final LoginProtectedAuthFlowContextLoader loader;
        private final LoginOtpVerifyPersistencePort otpVerifyPersistence;
        private final AuthFlowSessionMutationLockPort mutationLock;
        private final AuthFlowSessionPort sessions;
        private final SessionRevisionGenerator revisions;
        private final SecureLoginOtpVerifyReferenceGenerator references;
        private final LoginOtpVerifyRequestHasher requestHasher;
        private final Duration lockLease;
        private final LoginOtpVerifiedSessionPort verifiedSession;
        private final AuthFlowSessionCancellationPort cancellation;
        private final GetLoginAccountsUseCase accountLookup;
        private final LoginAccountRecordValidationService accountValidation;
        private final LoginAccountReferenceAssignmentService accountAssignment;
        private final EstablishAuthenticatedLoginSessionUseCase transition;
        private final Clock clock;

        public LoginOtpVerifyService(LoginProtectedAuthFlowContextLoader l, LoginOtpVerifyPersistencePort persistence,
                        AuthFlowSessionMutationLockPort mutationLock, AuthFlowSessionPort sessions,
                        SessionRevisionGenerator revisions,
                        SecureLoginOtpVerifyReferenceGenerator references, LoginOtpVerifyRequestHasher requestHasher,
                        Duration lockLease,
                        LoginOtpVerifiedSessionPort v, AuthFlowSessionCancellationPort c, GetLoginAccountsUseCase g,
                        LoginAccountRecordValidationService validation,
                        LoginAccountReferenceAssignmentService assignment,
                        EstablishAuthenticatedLoginSessionUseCase transition, Clock k) {
                loader = Objects.requireNonNull(l);
                otpVerifyPersistence = Objects.requireNonNull(persistence);
                this.mutationLock = Objects.requireNonNull(mutationLock);
                this.sessions = Objects.requireNonNull(sessions);
                this.revisions = Objects.requireNonNull(revisions);
                this.references = Objects.requireNonNull(references);
                this.requestHasher = Objects.requireNonNull(requestHasher);
                this.lockLease = Objects.requireNonNull(lockLease);
                verifiedSession = Objects.requireNonNull(v);
                cancellation = Objects.requireNonNull(c);
                accountLookup = Objects.requireNonNull(g);
                accountValidation = Objects.requireNonNull(validation);
                accountAssignment = Objects.requireNonNull(assignment);
                this.transition = Objects.requireNonNull(transition);
                clock = Objects.requireNonNull(k);
        }

        public Mono<VerifyLoginOtpResult> verify(VerifyLoginOtpCommand c) {
                return loader
                                .load(new LoginProtectedAuthFlowContextCommand(c.authFlowSessionId(), c.csrfToken(),
                                                c.origin(),
                                                c.referer(), c.correlationId(), AuthFlowStatus.OTP_SENT, true))
                                .flatMap(x -> x.isValid() ? verifyActive(c, x)
                                                : Mono.just(result(x
                                                                .status() == LoginProtectedAuthFlowContextStatus.CSRF_INVALID
                                                                                ? VerifyLoginOtpStatus.CSRF_INVALID
                                                                                : VerifyLoginOtpStatus.AUTH_FLOW_INVALID,
                                                                null, false)))
                                .doOnNext(r -> LOG.info("event=login.otp.verify.result status={}", r.status()))
                                .doOnError(
                                                e -> LOG.error("event=login.otp.verify.failure exceptionType={}",
                                                                e.getClass().getSimpleName()))
                                .onErrorReturn(result(VerifyLoginOtpStatus.SYSTEM_FAILED, null, false));
        }

        private Mono<VerifyLoginOtpResult> verifyActive(VerifyLoginOtpCommand c,
                        LoginProtectedAuthFlowContextResult x) {
                AuthFlowSessionId sid = new AuthFlowSessionId(c.authFlowSessionId());
                String reservationHash = requestHasher.hash();
                AuthenticatedLoginHandoffReference handoff = references.handoff();
                LoginOtpVerificationOperationReference operation = references.operation();
                return mutationLock.acquire(sid, lockLease).flatMap(lock -> {
                        if (!lock.acquired()) {
                                return Mono.just(result(VerifyLoginOtpStatus.SYSTEM_FAILED, null, false));
                        }
                        Mono<VerifyLoginOtpResult> operationFlow = sessions.loadSnapshot(sid)
                                        .flatMap(snapshot -> {
                                                if (snapshot.isEmpty()) {
                                                        return Mono.just(result(VerifyLoginOtpStatus.AUTH_FLOW_INVALID,
                                                                        null, false));
                                                }
                                                var command = new RevisionedLoginOtpVerifyCommand(lock,
                                                                snapshot.orElseThrow(),
                                                                x.activeOtpChallengeId(), reservationHash, handoff,
                                                                operation,
                                                                new RedactedSubmittedOtp(c.otpCode()), clock.instant(),
                                                                revisions.generate());
                                                return otpVerifyPersistence.verify(command)
                                                                .flatMap(r -> switch (r.outcome()) {
                                                                        case VERIFIED_AND_CONSUMED -> verifiedSession
                                                                                        .markVerified(c.authFlowSessionId(),
                                                                                                        c.correlationId())
                                                                                        .then(Mono.defer(
                                                                                                        () -> continueToAccounts(c, x)));
                                                                        case INVALID -> Mono.just(result(
                                                                                        VerifyLoginOtpStatus.INVALID,
                                                                                        r.remainingAttempts(), false));
                                                                        case EXPIRED -> Mono.just(result(
                                                                                        VerifyLoginOtpStatus.EXPIRED,
                                                                                        null, false));
                                                                        case LOCKED -> cancellation.cancel(sid,
                                                                                        AuthFlowSessionCancellationReason.SECURITY_REJECTION,
                                                                                        clock.instant())
                                                                                        .thenReturn(result(
                                                                                                        VerifyLoginOtpStatus.VERIFY_ATTEMPTS_EXCEEDED,
                                                                                                        0, true));
                                                                        case ALREADY_COMMITTED -> Mono.just(result(
                                                                                        VerifyLoginOtpStatus.SYSTEM_FAILED,
                                                                                        null, false));
                                                                        default -> Mono.just(result(
                                                                                        VerifyLoginOtpStatus.SYSTEM_FAILED,
                                                                                        null, false));
                                                                });
                                        })
                                        .switchIfEmpty(Mono
                                                        .just(result(VerifyLoginOtpStatus.SYSTEM_FAILED, null, false)));
                        return operationFlow
                                        .materialize()
                                        .flatMap(signal -> mutationLock.release(lock)
                                                        .onErrorReturn(false)
                                                        .thenReturn(signal))
                                        .dematerialize();
                });
        }

        private Mono<VerifyLoginOtpResult> continueToAccounts(VerifyLoginOtpCommand c,
                        LoginProtectedAuthFlowContextResult x) {
                var context = x.context();
                return accountLookup.getAccounts(
                                new GetLoginAccountsCommand(context.normalizedUsername(), context.custType(),
                                                c.correlationId()))
                                .flatMap(auth -> {
                                        if (auth.status() == LoginAccountAuthorizationStatus.UNAVAILABLE)
                                                return Mono.just(result(VerifyLoginOtpStatus.ACCOUNT_LIST_UNAVAILABLE,
                                                                null, false));
                                        var validated = accountValidation.validate(auth.accounts());
                                        if (validated.status() == LoginAccountValidationStatus.ZERO_VALID_RECORDS)
                                                return Mono.just(result(VerifyLoginOtpStatus.ZERO_VALID_ACCOUNTS, null,
                                                                false));
                                        var assigned = accountAssignment.assign(validated.accounts());
                                        var actor = new ActorContext(context.normalizedUsername(), context.userRole());
                                        var command = new EstablishAuthenticatedLoginSessionCommand(
                                                        new AuthFlowSessionId(c.authFlowSessionId()), actor,
                                                        assigned.accounts(), x.csrfTokenHash(),
                                                        c.correlationId());
                                        return transition.establish(command).map(
                                                        established -> established
                                                                        .status() == EstablishAuthenticatedLoginSessionStatus.ESTABLISHED
                                                                                        ? new VerifyLoginOtpResult(
                                                                                                        VerifyLoginOtpStatus.ACCOUNTS_READY,
                                                                                                        null,
                                                                                                        established.clearAuthFlowCookie(),
                                                                                                        assigned.accounts(),
                                                                                                        established.authenticatedSessionId(),
                                                                                                        established.authenticatedCsrfToken())
                                                                                        : result(VerifyLoginOtpStatus.SESSION_CREATION_FAILED,
                                                                                                        null, false));
                                });
        }

        private VerifyLoginOtpResult result(VerifyLoginOtpStatus s, Integer remaining, boolean clear) {
                return new VerifyLoginOtpResult(s, remaining, clear, List.of(), null, null);
        }
}
