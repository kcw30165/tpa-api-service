package com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.identity;

import static com.bct.ngtpa.apiservice.application.dto.auth.forgotpassword.context.ForgotPasswordAuthFlowContextAttributeNames.*;
import com.bct.ngtpa.apiservice.application.dto.auth.forgotpassword.context.*;
import com.bct.ngtpa.apiservice.application.dto.session.*;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.*;
import com.bct.ngtpa.apiservice.application.port.in.VerifyForgotPasswordIdentityUseCase;
import com.bct.ngtpa.apiservice.application.port.out.*;
import com.bct.ngtpa.apiservice.application.service.auth.forgotpassword.context.*;
import java.time.*;
import java.util.*;
import reactor.core.publisher.Mono;

public final class VerifyForgotPasswordIdentityService implements VerifyForgotPasswordIdentityUseCase {
    private final ForgotPasswordProtectedAuthFlowContextLoader loader;
    private final ForgotPasswordAuthFlowContextProtectorPort protector;
    private final AuthFlowSessionPort sessions;
    private final AuthFlowSessionMutationLockPort locks;
    private final AuthFlowSessionExpiryRefreshPort expiry;
    private final ForgotPasswordIdentityVerifier verifier = new ForgotPasswordIdentityVerifier();
    private final Clock clock;
    private final Duration lease;

    public VerifyForgotPasswordIdentityService(ForgotPasswordProtectedAuthFlowContextLoader l,
            ForgotPasswordAuthFlowContextProtectorPort p, AuthFlowSessionPort s, AuthFlowSessionMutationLockPort x,
            AuthFlowSessionExpiryRefreshPort e, Clock c, Duration lease) {
        loader = l;
        protector = p;
        sessions = s;
        locks = x;
        expiry = e;
        clock = c;
        this.lease = lease;
        if (lease == null || lease.isZero() || lease.isNegative())
            throw new IllegalArgumentException("mutation lock lease must be positive");
    }

    public Mono<VerifyForgotPasswordIdentityResult> verify(VerifyForgotPasswordIdentityCommand c) {
        if (c == null || c.sessionId() == null)
            return Mono
                    .just(VerifyForgotPasswordIdentityResult.of(VerifyForgotPasswordIdentityStatus.INVALID_AUTH_FLOW));
        if (verifier.mapPublicIdentityType(c.identityType()) == null)
            return Mono.just(VerifyForgotPasswordIdentityResult.of(VerifyForgotPasswordIdentityStatus.INVALID_TYPE));
        return locks.acquire(c.sessionId(), lease)
                .flatMap(h -> h.acquired() ? locked(c, h)
                        : Mono.just(VerifyForgotPasswordIdentityResult
                                .of(VerifyForgotPasswordIdentityStatus.INVALID_AUTH_FLOW)))
                .onErrorReturn(VerifyForgotPasswordIdentityResult.of(VerifyForgotPasswordIdentityStatus.SYSTEM_FAILED));
    }

    private Mono<VerifyForgotPasswordIdentityResult> locked(VerifyForgotPasswordIdentityCommand c,
            SessionLockHandle h) {
        Mono<VerifyForgotPasswordIdentityResult> work = sessions.loadSnapshot(c.sessionId()).flatMap(o -> {
            if (o.isEmpty())
                return Mono.just(
                        VerifyForgotPasswordIdentityResult.of(VerifyForgotPasswordIdentityStatus.INVALID_AUTH_FLOW));
            AuthFlowSession reloaded = o.get().session();
            Instant now = Instant.now(clock);
            if (reloaded.isExpired(now) || reloaded.isTerminal()
                    || reloaded.scenario() != AuthFlowScenario.FORGOT_PASSWORD
                    || reloaded.status() != AuthFlowStatus.STARTED)
                return Mono.just(
                        VerifyForgotPasswordIdentityResult.of(VerifyForgotPasswordIdentityStatus.INVALID_AUTH_FLOW));
            var q = new ForgotPasswordProtectedAuthFlowContextCommand(c.sessionId(), c.csrfToken(), c.origin(),
                    c.referer(), c.requestId(), Set.of(AuthFlowStatus.STARTED), true);
            return loader.load(q).flatMap(r -> {
                if (!r.isValid())
                    return Mono.just(VerifyForgotPasswordIdentityResult
                            .of(r.status() == ForgotPasswordProtectedAuthFlowContextStatus.CSRF_INVALID
                                    || r.status() == ForgotPasswordProtectedAuthFlowContextStatus.ORIGIN_REFERER_INVALID
                                            ? VerifyForgotPasswordIdentityStatus.SECURITY_REJECTED
                                            : VerifyForgotPasswordIdentityStatus.INVALID_AUTH_FLOW));
                var context = r.context();
                if (context.phase() != ForgotPasswordProtectedPhase.USERNAME_VERIFIED)
                    return Mono.just(VerifyForgotPasswordIdentityResult
                            .of(VerifyForgotPasswordIdentityStatus.INVALID_AUTH_FLOW));
                if (!verifier.matches(c.identityType(), c.identityValue(), context.publicIdentityType(),
                        context.tasUserId()))
                    return Mono.just(
                            VerifyForgotPasswordIdentityResult.of(VerifyForgotPasswordIdentityStatus.INCORRECT_ID));
                var verifiedContext = context.withPhase(ForgotPasswordProtectedPhase.IDENTITY_VERIFIED);
                return sessions.loadSnapshot(c.sessionId()).flatMap(latest -> {
                    if (latest.isEmpty()) {
                        return Mono.just(VerifyForgotPasswordIdentityResult
                                .of(VerifyForgotPasswordIdentityStatus.INVALID_AUTH_FLOW));
                    }
                    AuthFlowSession current = latest.get().session();
                    if (current.isExpired(now) || current.isTerminal()
                            || current.scenario() != AuthFlowScenario.FORGOT_PASSWORD
                            || current.status() != AuthFlowStatus.STARTED) {
                        return Mono.just(VerifyForgotPasswordIdentityResult
                                .of(VerifyForgotPasswordIdentityStatus.INVALID_AUTH_FLOW));
                    }
                    var encrypted = protector.protect(verifiedContext);
                    var attrs = new LinkedHashMap<>(current.data().attributes());
                    attrs.put(CONTEXT_CIPHERTEXT, encrypted.ciphertext());
                    attrs.put(CONTEXT_KEY_VERSION, encrypted.keyVersion());
                    attrs.put(CONTEXT_ALGORITHM, encrypted.algorithm());
                    AuthFlowSession updated = new AuthFlowSession(current.authFlowSessionId(), current.scenario(),
                            AuthFlowStatus.IDENTITY_VERIFIED, current.csrfTokenHash(), current.activeOtpChallengeId(),
                            new AuthFlowSessionData(current.data().contactType(), current.data().maskedDestination(),
                                    attrs),
                            current.createdAt(), now, current.expiresAt());
                    return new com.bct.ngtpa.apiservice.application.usecase.session.authflow.RevisionAwareAuthFlowSessionWriter(
                            sessions).updateExpected(current, updated)
                            .thenReturn(VerifyForgotPasswordIdentityResult
                                    .of(VerifyForgotPasswordIdentityStatus.VERIFIED));
                });
            });
        }).onErrorReturn(VerifyForgotPasswordIdentityResult.of(VerifyForgotPasswordIdentityStatus.SYSTEM_FAILED));
        return Mono.usingWhen(
                Mono.just(h),
                ignored -> work,
                ignored -> locks.release(h).onErrorReturn(false).then(),
                (ignored, error) -> locks.release(h).onErrorReturn(false).then(),
                ignored -> locks.release(h).onErrorReturn(false).then());
    }
}
