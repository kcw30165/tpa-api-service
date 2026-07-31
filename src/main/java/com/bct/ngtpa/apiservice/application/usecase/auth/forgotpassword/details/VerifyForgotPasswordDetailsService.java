package com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.details;

import com.bct.ngtpa.apiservice.application.dto.auth.forgotpassword.context.ForgotPasswordProtectedPhase;
import com.bct.ngtpa.apiservice.application.dto.session.SessionLockHandle;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.*;
import com.bct.ngtpa.apiservice.application.port.in.VerifyForgotPasswordDetailsUseCase;
import com.bct.ngtpa.apiservice.application.port.out.*;
import com.bct.ngtpa.apiservice.application.service.auth.forgotpassword.context.*;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
public final class VerifyForgotPasswordDetailsService implements VerifyForgotPasswordDetailsUseCase {
    private final ForgotPasswordDetailsValidator validator = new ForgotPasswordDetailsValidator();
    private final AuthFlowSessionPort sessions;
    private final AuthFlowSessionMutationLockPort locks;
    private final ForgotPasswordProtectedAuthFlowContextLoader loader;
    private final ForgotPasswordDetailsLookupPort lookup;
    private final Clock clock;

    public VerifyForgotPasswordDetailsService(AuthFlowSessionPort sessions, AuthFlowSessionMutationLockPort locks,
            ForgotPasswordProtectedAuthFlowContextLoader loader, ForgotPasswordDetailsLookupPort lookup, Clock clock) {
        this.sessions = Objects.requireNonNull(sessions);
        this.locks = Objects.requireNonNull(locks);
        this.loader = Objects.requireNonNull(loader);
        this.lookup = Objects.requireNonNull(lookup);
        this.clock = Objects.requireNonNull(clock);
    }

    public Mono<ForgotPasswordDetailsResult> verify(ForgotPasswordDetailsCommand c) {
        if (c == null || c.sessionId() == null)
            return Mono.just(ForgotPasswordDetailsResult.of(ForgotPasswordDetailsStatus.INVALID_AUTH_FLOW));
        var validation = validator.validate(c.dateOfBirth(), c.email(), c.mobile());
        if (!validation.valid())
            return Mono.just(new ForgotPasswordDetailsResult(ForgotPasswordDetailsStatus.VALIDATION_FAILED,
                    validation.targets()));
        Duration lease = c.lockLease() == null ? Duration.ofSeconds(10) : c.lockLease();
        return locks.acquire(c.sessionId(), lease)
                .flatMap(h -> h.acquired() ? locked(c, h)
                        : Mono.just(ForgotPasswordDetailsResult.of(ForgotPasswordDetailsStatus.INVALID_AUTH_FLOW)))
                .onErrorReturn(ForgotPasswordDetailsResult.of(ForgotPasswordDetailsStatus.SYSTEM_FAILED));
    }

    private Mono<ForgotPasswordDetailsResult> locked(ForgotPasswordDetailsCommand c, SessionLockHandle h) {
        Mono<ForgotPasswordDetailsResult> work = sessions.load(c.sessionId()).flatMap(optional -> {
            if (optional.isEmpty())
                return Mono.just(ForgotPasswordDetailsResult.of(ForgotPasswordDetailsStatus.INVALID_AUTH_FLOW));
            var session = optional.get();
            Instant now = Instant.now(clock);
            if (session.isExpired(now) || session.isTerminal() || session.scenario() != AuthFlowScenario.FORGOT_PASSWORD
                    || session.status() != AuthFlowStatus.STARTED)
                return Mono.just(ForgotPasswordDetailsResult.of(ForgotPasswordDetailsStatus.INVALID_AUTH_FLOW));
            var load = new ForgotPasswordProtectedAuthFlowContextCommand(c.sessionId(), c.csrfToken(), c.origin(),
                    c.referer(), c.requestId(), Set.of(AuthFlowStatus.STARTED), true);
            return loader.load(load).flatMap(result -> {
                if (!result.isValid())
                    return Mono.just(ForgotPasswordDetailsResult.of(
                            result.status() == ForgotPasswordProtectedAuthFlowContextStatus.CSRF_INVALID || result
                                    .status() == ForgotPasswordProtectedAuthFlowContextStatus.ORIGIN_REFERER_INVALID
                                            ? ForgotPasswordDetailsStatus.SECURITY_REJECTED
                                            : ForgotPasswordDetailsStatus.INVALID_AUTH_FLOW));
                var context = result.context();
                if (context.phase() != ForgotPasswordProtectedPhase.IDENTITY_VERIFIED)
                    return Mono.just(ForgotPasswordDetailsResult.of(ForgotPasswordDetailsStatus.INVALID_AUTH_FLOW));
                return lookup
                        .verify(new ForgotPasswordDetailsLookupCommand(context.publicIdentityType(),
                                context.tasUserId(), c.dateOfBirth(), c.email(), c.mobile(), context.customerId(),
                                c.requestId()))
                        .map(r -> r.status() == ForgotPasswordDetailsLookupStatus.MATCHED
                                ? ForgotPasswordDetailsResult.of(ForgotPasswordDetailsStatus.DETAILS_VERIFIED)
                                : ForgotPasswordDetailsResult.of(ForgotPasswordDetailsStatus.BUSINESS_REJECTED));
            });
        }).onErrorReturn(ForgotPasswordDetailsResult.of(ForgotPasswordDetailsStatus.SYSTEM_FAILED));
        return work.doFinally(signal -> locks.release(h).onErrorReturn(false).subscribe());
    }
}
