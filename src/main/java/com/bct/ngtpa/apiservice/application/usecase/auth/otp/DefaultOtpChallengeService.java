package com.bct.ngtpa.apiservice.application.usecase.auth.otp;

import com.bct.ngtpa.apiservice.application.dto.auth.otp.CreateOtpChallengeCommand;
import com.bct.ngtpa.apiservice.application.dto.auth.otp.CreateOtpChallengeResult;
import com.bct.ngtpa.apiservice.application.dto.auth.otp.InvalidateOtpChallengeCommand;
import com.bct.ngtpa.apiservice.application.dto.auth.otp.OtpChallenge;
import com.bct.ngtpa.apiservice.application.dto.auth.otp.OtpStatus;
import com.bct.ngtpa.apiservice.application.dto.auth.otp.OtpVerificationOutcome;
import com.bct.ngtpa.apiservice.application.dto.auth.otp.ReplaceOtpChallengeCommand;
import com.bct.ngtpa.apiservice.application.dto.auth.otp.ReplaceOtpChallengeResult;
import com.bct.ngtpa.apiservice.application.dto.auth.otp.VerifyOtpChallengeCommand;
import com.bct.ngtpa.apiservice.application.dto.auth.otp.VerifyOtpChallengeResult;
import com.bct.ngtpa.apiservice.application.port.out.OtpChallengePort;
import com.bct.ngtpa.apiservice.application.port.out.OtpChallengeStatePort;
import com.bct.ngtpa.apiservice.application.service.auth.otp.OtpCodeHasher;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

@RequiredArgsConstructor
public class DefaultOtpChallengeService implements OtpChallengePort {

    private final OtpChallengeStatePort otpChallengeStatePort;
    private final OtpCodeHasher otpCodeHasher;
    private final Clock clock;

    @Override
    public Mono<CreateOtpChallengeResult> createChallenge(CreateOtpChallengeCommand command) {
        return Mono.error(new UnsupportedOperationException("OTP challenge creation is not implemented in P1.4"));
    }

    @Override
    public Mono<VerifyOtpChallengeResult> verifyChallenge(VerifyOtpChallengeCommand command) {
        return Mono.defer(() -> {
            validateCommand(command);
            Instant now = command.now() != null ? command.now() : Instant.now(clock);

            return otpChallengeStatePort.findById(command.otpChallengeId())
                    .flatMap(optionalChallenge -> verifyLoadedChallenge(command, now, optionalChallenge));
        });
    }

    @Override
    public Mono<ReplaceOtpChallengeResult> replaceChallenge(ReplaceOtpChallengeCommand command) {
        return Mono.error(new UnsupportedOperationException("OTP challenge replacement is not implemented in P1.4"));
    }

    @Override
    public Mono<Void> invalidateChallenge(InvalidateOtpChallengeCommand command) {
        return Mono.error(new UnsupportedOperationException("OTP challenge invalidation is not implemented in P1.4"));
    }

    private Mono<VerifyOtpChallengeResult> verifyLoadedChallenge(
            VerifyOtpChallengeCommand command,
            Instant now,
            Optional<OtpChallenge> optionalChallenge) {
        if (optionalChallenge.isEmpty()) {
            return Mono.just(result(command.otpChallengeId(), OtpVerificationOutcome.NOT_FOUND, 0));
        }

        OtpChallenge challenge = optionalChallenge.orElseThrow();
        if (challenge.status() == OtpStatus.INVALIDATED) {
            return Mono.just(result(challenge.otpChallengeId(), OtpVerificationOutcome.INVALIDATED, 0));
        }
        if (challenge.status() == OtpStatus.LOCKED) {
            return Mono.just(result(challenge.otpChallengeId(), OtpVerificationOutcome.LOCKED, 0));
        }
        if (challenge.status() == OtpStatus.VERIFIED || challenge.status() == OtpStatus.CONSUMED) {
            return Mono.just(result(challenge.otpChallengeId(), OtpVerificationOutcome.ALREADY_VERIFIED, 0));
        }
        if (challenge.status() == OtpStatus.EXPIRED || !challenge.expiresAt().isAfter(now)) {
            OtpChallenge expiredChallenge = copyChallenge(
                    challenge,
                    OtpStatus.EXPIRED,
                    challenge.failedAttemptCount(),
                    challenge.invalidatedAt(),
                    challenge.verifiedAt());
            return otpChallengeStatePort.update(expiredChallenge)
                    .thenReturn(result(challenge.otpChallengeId(), OtpVerificationOutcome.EXPIRED, 0));
        }
        if (challenge.status() != OtpStatus.CREATED) {
            return Mono.just(result(challenge.otpChallengeId(), OtpVerificationOutcome.INVALIDATED, 0));
        }
        if (otpCodeHasher.matches(command.code(), challenge.codeHash())) {
            OtpChallenge verifiedChallenge = copyChallenge(
                    challenge,
                    OtpStatus.VERIFIED,
                    challenge.failedAttemptCount(),
                    challenge.invalidatedAt(),
                    now);
            return otpChallengeStatePort.update(verifiedChallenge)
                    .thenReturn(result(challenge.otpChallengeId(), OtpVerificationOutcome.VALID,
                            remainingAttempts(challenge.maxVerifyAttempts(), challenge.failedAttemptCount())));
        }

        int updatedFailedAttemptCount = challenge.failedAttemptCount() + 1;
        boolean locked = updatedFailedAttemptCount >= challenge.maxVerifyAttempts();
        OtpStatus nextStatus = locked ? OtpStatus.LOCKED : OtpStatus.CREATED;
        OtpChallenge updatedChallenge = copyChallenge(
                challenge,
                nextStatus,
                updatedFailedAttemptCount,
                challenge.invalidatedAt(),
                challenge.verifiedAt());

        return otpChallengeStatePort.update(updatedChallenge)
                .thenReturn(result(
                        challenge.otpChallengeId(),
                        locked ? OtpVerificationOutcome.LOCKED : OtpVerificationOutcome.INVALID,
                        locked ? 0 : remainingAttempts(challenge.maxVerifyAttempts(), updatedFailedAttemptCount)));
    }

    private VerifyOtpChallengeResult result(String otpChallengeId, OtpVerificationOutcome outcome, int remainingAttempts) {
        return new VerifyOtpChallengeResult(otpChallengeId, outcome, remainingAttempts, null);
    }

    private int remainingAttempts(int maxVerifyAttempts, int failedAttemptCount) {
        return Math.max(0, maxVerifyAttempts - failedAttemptCount);
    }

    private OtpChallenge copyChallenge(
            OtpChallenge challenge,
            OtpStatus status,
            int failedAttemptCount,
            Instant invalidatedAt,
            Instant verifiedAt) {
        return new OtpChallenge(
                challenge.otpChallengeId(),
                challenge.scenario(),
                challenge.workflowRef(),
                challenge.channel(),
                challenge.destinationHash(),
                challenge.codeHash(),
                status,
                failedAttemptCount,
                challenge.maxVerifyAttempts(),
                challenge.resendAttemptCount(),
                challenge.maxResendAttempts(),
                challenge.createdAt(),
                challenge.expiresAt(),
                challenge.lastSentAt(),
                invalidatedAt,
                verifiedAt);
    }

    private void validateCommand(VerifyOtpChallengeCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("Verify OTP challenge command must not be null");
        }
        if (command.otpChallengeId() == null || command.otpChallengeId().isBlank()) {
            throw new IllegalArgumentException("OTP challenge ID must not be blank");
        }
        if (command.code() == null || command.code().isBlank()) {
            throw new IllegalArgumentException("OTP code must not be blank");
        }
    }
}