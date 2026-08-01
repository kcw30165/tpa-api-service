package com.bct.ngtpa.apiservice.adapter.out.redis.auth.login.otp.verify;

import com.bct.ngtpa.apiservice.application.dto.auth.login.otp.verify.*;
import com.bct.ngtpa.apiservice.application.dto.auth.otp.*;
import com.bct.ngtpa.apiservice.application.port.out.*;
import com.bct.ngtpa.apiservice.application.service.auth.otp.OtpCodeHasher;
import java.util.Objects;
import reactor.core.publisher.Mono;

public final class RedisLoginOtpVerifyPersistenceAdapter implements LoginOtpVerifyPersistencePort {
    private final OtpChallengeStatePort challenges;
    private final OtpCodeHasher hasher;

    public RedisLoginOtpVerifyPersistenceAdapter(OtpChallengeStatePort challenges, OtpCodeHasher hasher) {
        this.challenges = Objects.requireNonNull(challenges);
        this.hasher = Objects.requireNonNull(hasher);
    }

    @Override
    public Mono<LoginOtpVerifyPersistenceResult> verify(RevisionedLoginOtpVerifyCommand c) {
        return challenges.findById(c.expectedChallengeId()).flatMap(found -> {
            if (found.isEmpty())
                return Mono.just(out(LoginOtpVerifyPersistenceOutcome.CHALLENGE_NOT_FOUND, 0, null));
            OtpChallenge x = found.orElseThrow();
            if (x.status() == OtpStatus.VERIFIED || x.status() == OtpStatus.CONSUMED)
                return Mono.just(out(LoginOtpVerifyPersistenceOutcome.ALREADY_COMMITTED, 0, null));
            if (x.status() == OtpStatus.LOCKED)
                return Mono.just(out(LoginOtpVerifyPersistenceOutcome.LOCKED, 0, null));
            if (x.status() == OtpStatus.EXPIRED || !x.expiresAt().isAfter(c.verificationTime()))
                return Mono.just(out(LoginOtpVerifyPersistenceOutcome.EXPIRED, 0, null));
            if (x.status() != OtpStatus.CREATED)
                return Mono.just(out(LoginOtpVerifyPersistenceOutcome.EXPECTED_STATE_CHANGED, 0, null));
            boolean match = hasher.matches(c.submittedOtp().revealForComparison(), x.codeHash());
            int failures = x.failedAttemptCount() + (match ? 0 : 1);
            boolean locked = failures >= x.maxVerifyAttempts();
            OtpStatus status = match ? OtpStatus.CONSUMED : (locked ? OtpStatus.LOCKED : OtpStatus.CREATED);
            OtpChallenge updated = new OtpChallenge(x.otpChallengeId(), x.scenario(), x.workflowRef(), x.channel(),
                    x.destinationHash(), x.codeHash(), status, failures, x.maxVerifyAttempts(), x.resendAttemptCount(),
                    x.maxResendAttempts(), x.createdAt(), x.expiresAt(), x.lastSentAt(), x.invalidatedAt(),
                    match ? c.verificationTime() : x.verifiedAt());
            LoginOtpConsumptionProof proof = match
                    ? new LoginOtpConsumptionProof(c.reservationHash(), c.handoffReference(), c.operationReference(),
                            x.otpChallengeId(), c.verificationTime())
                    : null;
            return challenges.update(updated)
                    .thenReturn(out(
                            match ? LoginOtpVerifyPersistenceOutcome.VERIFIED_AND_CONSUMED
                                    : (locked ? LoginOtpVerifyPersistenceOutcome.LOCKED
                                            : LoginOtpVerifyPersistenceOutcome.INVALID),
                            Math.max(0, x.maxVerifyAttempts() - failures), proof));
        }).onErrorReturn(out(LoginOtpVerifyPersistenceOutcome.SYSTEM_FAILURE, null, null));
    }

    private LoginOtpVerifyPersistenceResult out(LoginOtpVerifyPersistenceOutcome o, Integer r,
            LoginOtpConsumptionProof p) {
        return new LoginOtpVerifyPersistenceResult(o, r, p);
    }
}
