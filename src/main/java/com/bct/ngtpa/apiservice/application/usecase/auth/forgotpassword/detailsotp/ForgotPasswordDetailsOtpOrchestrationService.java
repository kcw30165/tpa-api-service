package com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.detailsotp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import static com.bct.ngtpa.apiservice.application.dto.auth.forgotpassword.context.ForgotPasswordAuthFlowContextAttributeNames.*;
import com.bct.ngtpa.apiservice.application.dto.auth.forgotpassword.context.*;
import com.bct.ngtpa.apiservice.application.dto.auth.forgotpassword.idempotency.*;
import com.bct.ngtpa.apiservice.application.dto.auth.otp.*;
import com.bct.ngtpa.apiservice.application.dto.message.*;
import com.bct.ngtpa.apiservice.application.dto.session.SessionLockHandle;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.*;
import com.bct.ngtpa.apiservice.application.port.in.ProcessForgotPasswordDetailsOtpUseCase;
import com.bct.ngtpa.apiservice.application.port.out.*;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.details.*;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.otp.*;
import com.bct.ngtpa.apiservice.shared.logging.LogExecution;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;
import reactor.core.publisher.Mono;

public class ForgotPasswordDetailsOtpOrchestrationService implements ProcessForgotPasswordDetailsOtpUseCase {
        private static final Logger LOGGER = LoggerFactory
                        .getLogger(ForgotPasswordDetailsOtpOrchestrationService.class);
        private static final ForgotPasswordOtpTemplates OTP_TEMPLATES = ForgotPasswordOtpTemplates.approved();
        private final ForgotPasswordDetailsValidator validator = new ForgotPasswordDetailsValidator();
        private final ForgotPasswordIdempotencyPort idem;
        private final SecureForgotPasswordOtpCommitRefGenerator refs;
        private final AuthFlowSessionMutationLockPort locks;
        private final AuthFlowSessionPort sessions;
        private final ForgotPasswordAuthFlowContextProtectorPort protector;
        private final ForgotPasswordUnderLockDetailsVerifier details;
        private final OtpChallengePort otp;
        private final MessageDeliveryPort delivery;
        private final ForgotPasswordDestinationMasker masker;
        private final ForgotPasswordOtpSessionBindingPort binding;
        private final ForgotPasswordOtpCommitCoordinator commits;
        private final ForgotPasswordOtpReplayResolver replay;
        private final ForgotPasswordDetailsOtpLeasePolicy policy;
        private final Clock clock;
        private final Duration idemTtl;

        public ForgotPasswordDetailsOtpOrchestrationService(ForgotPasswordIdempotencyPort i,
                        SecureForgotPasswordOtpCommitRefGenerator r, AuthFlowSessionMutationLockPort l,
                        AuthFlowSessionPort s,
                        ForgotPasswordAuthFlowContextProtectorPort p, ForgotPasswordUnderLockDetailsVerifier d,
                        OtpChallengePort o,
                        MessageDeliveryPort m, ForgotPasswordDestinationMasker x, ForgotPasswordOtpSessionBindingPort b,
                        ForgotPasswordOtpCommitCoordinator c, ForgotPasswordOtpReplayResolver y,
                        ForgotPasswordDetailsOtpLeasePolicy q, Clock k, Duration ttl) {
                idem = i;
                refs = r;
                locks = l;
                sessions = s;
                protector = p;
                details = d;
                otp = o;
                delivery = m;
                masker = x;
                binding = b;
                commits = c;
                replay = y;
                policy = q;
                clock = k;
                idemTtl = ttl;
        }

        @LogExecution(value = "forgot-password.details-otp", logArgs = true, logResult = true)
        public Mono<ForgotPasswordDetailsOtpResult> process(ForgotPasswordDetailsOtpCommand c) {
                var v = validator.validate(c.dateOfBirth(), c.email(), c.mobile());
                if (!v.valid())
                        return Mono.just(new ForgotPasswordDetailsOtpResult(
                                        ForgotPasswordDetailsOtpResult.Status.VALIDATION_FAILED,
                                        null, 0, 0, v.targets()));
                Instant now = Instant.now(clock);
                ForgotPasswordIdempotencyKey key = new ForgotPasswordIdempotencyKey(c.idempotencyKey());
                ForgotPasswordRequestHash hash = new ForgotPasswordRequestHash(
                                hex(c.dateOfBirth() + "|" + Objects.toString(c.email(), "") + "|"
                                                + Objects.toString(c.mobile(), "")));
                return idem
                                .startProcessing(new ForgotPasswordIdempotencyStartCommand(
                                                ForgotPasswordIdempotencyAction.DETAILS_AND_OTP, key, hash, now,
                                                now.plus(idemTtl), idemTtl))
                                .flatMap(r -> switch (r.outcome()) {
                                        case COMPLETED_REPLAY ->
                                                replay.resolve(r.record().replaySnapshot().opaqueCommitRef())
                                                                .map(this::fromReplay);
                                        case STARTED -> reserveAndLock(c, r.record());
                                        default -> Mono.just(systemResult());
                                });
        }

        private Mono<ForgotPasswordDetailsOtpResult> reserveAndLock(ForgotPasswordDetailsOtpCommand c,
                        ForgotPasswordIdempotencyRecord r) {
                String candidate = r.pendingOpaqueCommitRef() == null ? refs.generate() : r.pendingOpaqueCommitRef();
                return idem
                                .attachPendingCommitRef(new ForgotPasswordPendingCommitRefCommand(r.action(),
                                                r.idempotencyKey(),
                                                r.requestHash(), candidate, idemTtl))
                                .flatMap(p -> locks.acquire(c.sessionId(), policy.lease())
                                                .flatMap(h -> h.acquired() ? locked(c, r, p.opaqueCommitRef(), h)
                                                                : Mono.just(systemResult())));
        }

        private Mono<ForgotPasswordDetailsOtpResult> locked(ForgotPasswordDetailsOtpCommand c,
                        ForgotPasswordIdempotencyRecord r, String ref,
                        com.bct.ngtpa.apiservice.application.dto.session.SessionLockHandle h) {
                var budget = new ForgotPasswordMutationLeaseBudget(policy.lease(), System::nanoTime);
                Mono<ForgotPasswordDetailsOtpResult> work = sessions.loadSnapshot(c.sessionId()).flatMap(o -> {
                        if (o.isEmpty())
                                return Mono.just(systemResult());
                        AuthFlowSession s = o.get().session();
                        if (s.scenario() != AuthFlowScenario.FORGOT_PASSWORD || s.status() != AuthFlowStatus.STARTED
                                        || s.isExpired(Instant.now(clock)))
                                return Mono.just(systemResult());
                        ForgotPasswordAuthFlowContext ctx = decrypt(s);
                        if (ctx.phase() != ForgotPasswordProtectedPhase.IDENTITY_VERIFIED)
                                return Mono.just(systemResult());
                        return details.verify(h, s, ctx, c.dateOfBirth(), c.email(), c.mobile(), c.requestId())
                                        .flatMap(d -> d.status() == ForgotPasswordDetailsStatus.DETAILS_VERIFIED
                                                        ? issue(c, r, ref, h, s, ctx, budget)
                                                        : Mono.just(new ForgotPasswordDetailsOtpResult(
                                                                        ForgotPasswordDetailsOtpResult.Status.BUSINESS_REJECTED,
                                                                        null, 0, 0,
                                                                        List.of("PAGE"))));
                }).doOnError(error -> LOGGER.error(
                                "event=forgot-password.details-otp.exception exceptionType={} message={}",
                                error.getClass().getName(), error.getMessage(), error))
                                .onErrorReturn(systemResult());
                return Mono.usingWhen(
                                Mono.just(h),
                                ignored -> work,
                                ignored -> locks.release(h).onErrorReturn(false).then(),
                                (ignored, error) -> locks.release(h).onErrorReturn(false).then(),
                                ignored -> locks.release(h).onErrorReturn(false).then());
        }

        private Mono<ForgotPasswordDetailsOtpResult> issue(ForgotPasswordDetailsOtpCommand c,
                        ForgotPasswordIdempotencyRecord r, String ref,
                        com.bct.ngtpa.apiservice.application.dto.session.SessionLockHandle h, AuthFlowSession s,
                        ForgotPasswordAuthFlowContext ctx, ForgotPasswordMutationLeaseBudget budget) {
                String channel = c.email() != null && !c.email().isBlank() ? "email" : "mobile";
                String dest = "email".equals(channel) ? c.email() : c.mobile();
                OtpChannel oc = "email".equals(channel) ? OtpChannel.EMAIL : OtpChannel.SMS;
                return otp.createChallenge(
                                new CreateOtpChallengeCommand(OtpScenario.FORGOT_PASSWORD, c.sessionId().value(), oc,
                                                hex(dest), 6, true, 300, 5, 5, Instant.now(clock)))
                                .flatMap(ch -> {
                                        if (!budget.active())
                                                return Mono.just(systemResult());
                                        MessageDeliveryCommand msg = new MessageDeliveryCommand(
                                                        MessageDeliveryType.FORGOT_PASSWORD_OTP,
                                                        MessageDeliveryScenario.FORGOT_PASSWORD,
                                                        "email".equals(channel) ? MessageDeliveryChannel.EMAIL
                                                                        : MessageDeliveryChannel.SMS,
                                                        dest,
                                                        "email".equals(channel) ? OTP_TEMPLATES.emailSubject(c.language())
                                                                        : null,
                                                        "email".equals(channel) ? OTP_TEMPLATES.emailBody(c.language())
                                                                        : OTP_TEMPLATES.sms(c.language()),
                                                        null,
                                                        Map.of("code", ch.rawCode(), "ttlValue", "5", "ttlUnit",
                                                                        ttlUnit(c.language())),
                                                        c.language(),
                                                        c.requestId());
                                        return delivery.deliver(msg)
                                                        .flatMap(dr -> handleDelivery(c, r, ref, h, channel, dest, ch,
                                                                        dr, budget));
                                });
        }

        private Mono<ForgotPasswordDetailsOtpResult> handleDelivery(ForgotPasswordDetailsOtpCommand c,
                        ForgotPasswordIdempotencyRecord r, String ref,
                        SessionLockHandle h, String channel,
                        String dest,
                        CreateOtpChallengeResult ch, MessageDeliveryResult dr,
                        ForgotPasswordMutationLeaseBudget budget) {
                if (!budget.hasPersistenceBudget(policy.persistenceAllowance()))
                        return Mono.just(systemResult());
                MessageDeliveryStatus st = dr.status();
                Instant now = Instant.now(clock);
                var bind = new ForgotPasswordOtpBindingCommand(c.sessionId(), h, st, ch.otpChallengeId(), channel, dest,
                                masker.mask(channel, dest), ch.expiresAt(), now.plusSeconds(60), now);
                return binding.apply(bind).flatMap(br -> {
                        if (st == MessageDeliveryStatus.ACCEPTED && br.bound()) {
                                if (!budget.hasPersistenceBudget(policy.persistenceAllowance()))
                                        return Mono.just(systemResult());
                                var state = new ForgotPasswordOtpCommitState(c.sessionId().value(), ch.otpChallengeId(),
                                                now,
                                                ch.expiresAt());
                                return commits.complete(withPending(r, ref), state, now)
                                                .thenReturn(new ForgotPasswordDetailsOtpResult(
                                                                ForgotPasswordDetailsOtpResult.Status.SUCCESS,
                                                                masker.mask(channel, dest), ch.expiresInSeconds(), 60,
                                                                List.of()));
                        }
                        if (st == MessageDeliveryStatus.FAILED)
                                return idem.markFailed(
                                                new ForgotPasswordIdempotencyFailCommand(r.action(), r.idempotencyKey(),
                                                                r.requestHash(), now, idemTtl))
                                                .thenReturn(systemResult());
                        return idem
                                        .markUnknownOutcome(new ForgotPasswordIdempotencyUnknownOutcomeCommand(
                                                        r.action(),
                                                        r.idempotencyKey(), r.requestHash(), now, idemTtl))
                                        .thenReturn(systemResult());
                });
        }

        private ForgotPasswordIdempotencyRecord withPending(ForgotPasswordIdempotencyRecord r, String ref) {
                return new ForgotPasswordIdempotencyRecord(r.action(), r.idempotencyKey(), r.requestHash(), r.status(),
                                r.replaySnapshot(), r.createdAt(), r.updatedAt(), r.expiresAt(), ref);
        }

        private ForgotPasswordAuthFlowContext decrypt(AuthFlowSession s) {
                Map<String, String> a = s.data().attributes();
                return protector.unprotect(
                                new ForgotPasswordAuthFlowEncryptedContext(a.get(CONTEXT_CIPHERTEXT),
                                                a.get(CONTEXT_KEY_VERSION),
                                                a.getOrDefault(CONTEXT_ALGORITHM,
                                                                ForgotPasswordAuthFlowEncryptedContext.DEFAULT_ALGORITHM)));
        }

        private ForgotPasswordDetailsOtpResult fromReplay(ForgotPasswordOtpReplayResult r) {
                if (r.status() == ForgotPasswordOtpReplayResult.Status.ACTIVE)
                        return new ForgotPasswordDetailsOtpResult(ForgotPasswordDetailsOtpResult.Status.SUCCESS,
                                        r.maskedDestination(), r.expiresInSeconds(), r.resendAvailableInSeconds(),
                                        List.of());
                if (r.status() == ForgotPasswordOtpReplayResult.Status.EXPIRED)
                        return new ForgotPasswordDetailsOtpResult(ForgotPasswordDetailsOtpResult.Status.EXPIRED, null,
                                        0, 0,
                                        List.of("PAGE"));
                return systemResult();
        }

        private String hex(String v) {
                try {
                        return java.util.HexFormat.of()
                                        .formatHex(MessageDigest.getInstance("SHA-256")
                                                        .digest(v.getBytes(StandardCharsets.UTF_8)));
                } catch (Exception e) {
                        throw new IllegalStateException("hash unavailable");
                }
        }

        private String ttlUnit(String language) {
                return language != null && language.trim().replace('-', '_').equalsIgnoreCase("zh_HK")
                                ? "分鐘"
                                : "minutes";
        }

        private static ForgotPasswordDetailsOtpResult systemResult() {
                String stage = StackWalker.getInstance().walk(frames -> frames
                                .filter(frame -> frame.getClassName().equals(
                                                ForgotPasswordDetailsOtpOrchestrationService.class.getName()))
                                .map(StackWalker.StackFrame::getMethodName)
                                .filter(method -> !"systemResult".equals(method))
                                .findFirst()
                                .orElse("unknown"));
                LOGGER.error("event=forgot-password.details-otp.system-result stage={}", stage);
                return ForgotPasswordDetailsOtpResult.system();
        }
}
