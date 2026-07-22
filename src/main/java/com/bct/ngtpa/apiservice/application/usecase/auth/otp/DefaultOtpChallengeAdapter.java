package com.bct.ngtpa.apiservice.application.usecase.auth.otp;

import com.bct.ngtpa.apiservice.application.dto.auth.otp.CreateOtpChallengeCommand;
import com.bct.ngtpa.apiservice.application.dto.auth.otp.CreateOtpChallengeResult;
import com.bct.ngtpa.apiservice.application.dto.auth.otp.InvalidateOtpChallengeCommand;
import com.bct.ngtpa.apiservice.application.dto.auth.otp.OtpChallenge;
import com.bct.ngtpa.apiservice.application.dto.auth.otp.OtpReplacementOutcome;
import com.bct.ngtpa.apiservice.application.dto.auth.otp.OtpStatus;
import com.bct.ngtpa.apiservice.application.dto.auth.otp.OtpVerificationOutcome;
import com.bct.ngtpa.apiservice.application.dto.auth.otp.ReplaceOtpChallengeCommand;
import com.bct.ngtpa.apiservice.application.dto.auth.otp.ReplaceOtpChallengeResult;
import com.bct.ngtpa.apiservice.application.dto.auth.otp.VerifyOtpChallengeCommand;
import com.bct.ngtpa.apiservice.application.dto.auth.otp.VerifyOtpChallengeResult;
import com.bct.ngtpa.apiservice.application.port.out.OtpChallengePort;
import com.bct.ngtpa.apiservice.application.port.out.OtpChallengeStatePort;
import com.bct.ngtpa.apiservice.application.service.auth.otp.OtpChallengeIdGenerator;
import com.bct.ngtpa.apiservice.application.service.auth.otp.OtpCodeGenerator;
import com.bct.ngtpa.apiservice.application.service.auth.otp.OtpCodeHasher;

import lombok.extern.slf4j.Slf4j;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import reactor.core.publisher.Mono;

@Slf4j
public class DefaultOtpChallengeAdapter implements OtpChallengePort {

    private final OtpChallengeStatePort otpChallengeStatePort;
    private final OtpCodeGenerator otpCodeGenerator;
    private final OtpCodeHasher otpCodeHasher;
    private final OtpChallengeIdGenerator otpChallengeIdGenerator;
    private final Clock clock;

    public DefaultOtpChallengeAdapter(
            OtpChallengeStatePort otpChallengeStatePort,
            OtpCodeHasher otpCodeHasher,
            Clock clock) {
        this(otpChallengeStatePort, null, otpCodeHasher, null, clock);
    }

    public DefaultOtpChallengeAdapter(
            OtpChallengeStatePort otpChallengeStatePort,
            OtpCodeGenerator otpCodeGenerator,
            OtpCodeHasher otpCodeHasher,
            OtpChallengeIdGenerator otpChallengeIdGenerator,
            Clock clock) {
        this.otpChallengeStatePort = otpChallengeStatePort;
        this.otpCodeGenerator = otpCodeGenerator;
        this.otpCodeHasher = otpCodeHasher;
        this.otpChallengeIdGenerator = otpChallengeIdGenerator;
        this.clock = clock;
    }

    @Override
    public Mono<CreateOtpChallengeResult> createChallenge(CreateOtpChallengeCommand command) {
        return Mono.defer(() -> {
            validateCreateCommand(command);
            requireCreateDependencies();
            Instant now = command.now() != null ? command.now() : Instant.now(clock);
            String otpChallengeId = otpChallengeIdGenerator.generate();
            String rawCode = otpCodeGenerator.generate(command.length(), command.numericOnly());
            String codeHash = otpCodeHasher.hash(rawCode);
            Instant expiresAt = now.plusSeconds(command.ttlSeconds());

            OtpChallenge challenge = new OtpChallenge(
                    otpChallengeId,
                    command.scenario(),
                    command.workflowRef(),
                    command.channel(),
                    command.destinationHash(),
                    codeHash,
                    OtpStatus.CREATED,
                    0,
                    command.maxVerifyAttempts(),
                    0,
                    command.maxResendAttempts(),
                    now,
                    expiresAt,
                    now,
                    null,
                    null);

            return otpChallengeStatePort.save(challenge)
                    .thenReturn(new CreateOtpChallengeResult(otpChallengeId, rawCode, expiresAt, command.ttlSeconds()));
        });
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
        return Mono.defer(() -> {
            validateReplaceCommand(command);
            requireReplacementDependencies();
            Instant now = command.now() != null ? command.now() : Instant.now(clock);

            return otpChallengeStatePort.findById(command.currentOtpChallengeId())
                    .flatMap(optionalChallenge -> replaceLoadedChallenge(command, now, optionalChallenge));
        });
    }

    @Override
    public Mono<Void> invalidateChallenge(InvalidateOtpChallengeCommand command) {
        return Mono.defer(() -> {
            validateInvalidateCommand(command);
            Instant now = command.now() != null ? command.now() : Instant.now(clock);

            return otpChallengeStatePort.findById(command.otpChallengeId())
                    .flatMap(optionalChallenge -> invalidateLoadedChallenge(optionalChallenge, now));
        });
    }

    private Mono<Void> invalidateLoadedChallenge(Optional<OtpChallenge> optionalChallenge, Instant now) {
        if (optionalChallenge.isEmpty()) {
            return Mono.empty();
        }

        OtpChallenge challenge = optionalChallenge.orElseThrow();
        if (challenge.status() == OtpStatus.INVALIDATED
                || challenge.status() == OtpStatus.EXPIRED
                || challenge.status() == OtpStatus.LOCKED
                || challenge.status() == OtpStatus.CONSUMED) {
            return Mono.empty();
        }

        if (challenge.status() == OtpStatus.CREATED || challenge.status() == OtpStatus.VERIFIED) {
            OtpChallenge invalidatedChallenge = copyChallenge(
                    challenge,
                    OtpStatus.INVALIDATED,
                    challenge.failedAttemptCount(),
                    now,
                    challenge.verifiedAt());
            return otpChallengeStatePort.update(invalidatedChallenge);
        }

        return Mono.empty();
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
        boolean recoveringFromVerifyLock = challenge.status() == OtpStatus.LOCKED;
        if (challenge.status() != OtpStatus.CREATED && !recoveringFromVerifyLock) {
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

    private Mono<ReplaceOtpChallengeResult> replaceLoadedChallenge(
            ReplaceOtpChallengeCommand command,
            Instant now,
            Optional<OtpChallenge> optionalChallenge) {
        if (optionalChallenge.isEmpty()) {
            return Mono.just(replacementResult(
                    command.currentOtpChallengeId(),
                    null,
                    null,
                    OtpReplacementOutcome.NOT_FOUND,
                    null,
                    0,
                    command.resendCooldownSeconds(),
                    0,
                    null));
        }

        OtpChallenge challenge = optionalChallenge.orElseThrow();
        boolean recoveringFromVerifyLock = challenge.status() == OtpStatus.LOCKED;
        if (challenge.status() == OtpStatus.VERIFIED || challenge.status() == OtpStatus.CONSUMED) {
            return Mono.just(replacementResult(
                    challenge.otpChallengeId(),
                    null,
                    null,
                    OtpReplacementOutcome.ALREADY_VERIFIED,
                    null,
                    0,
                    command.resendCooldownSeconds(),
                    remainingResendAttempts(challenge.maxResendAttempts(), challenge.resendAttemptCount()),
                    null));
        }
        if (challenge.status() == OtpStatus.INVALIDATED) {
            return Mono.just(replacementResult(
                    challenge.otpChallengeId(),
                    null,
                    null,
                    OtpReplacementOutcome.INVALIDATED,
                    null,
                    0,
                    command.resendCooldownSeconds(),
                    remainingResendAttempts(challenge.maxResendAttempts(), challenge.resendAttemptCount()),
                    null));
        }
        if (challenge.status() == OtpStatus.EXPIRED || !challenge.expiresAt().isAfter(now)) {
            if (challenge.status() == OtpStatus.EXPIRED) {
                return Mono.just(replacementResult(
                        challenge.otpChallengeId(),
                        null,
                        null,
                        OtpReplacementOutcome.EXPIRED,
                        null,
                        0,
                        command.resendCooldownSeconds(),
                        remainingResendAttempts(challenge.maxResendAttempts(), challenge.resendAttemptCount()),
                        null));
            }

            OtpChallenge expiredChallenge = copyChallenge(
                    challenge,
                    challenge.otpChallengeId(),
                    challenge.codeHash(),
                    OtpStatus.EXPIRED,
                    challenge.failedAttemptCount(),
                    challenge.resendAttemptCount(),
                    challenge.createdAt(),
                    challenge.expiresAt(),
                    challenge.lastSentAt(),
                    challenge.invalidatedAt(),
                    challenge.verifiedAt());
            return otpChallengeStatePort.update(expiredChallenge)
                    .thenReturn(replacementResult(
                            challenge.otpChallengeId(),
                            null,
                            null,
                            OtpReplacementOutcome.EXPIRED,
                            null,
                            0,
                            command.resendCooldownSeconds(),
                            remainingResendAttempts(challenge.maxResendAttempts(), challenge.resendAttemptCount()),
                            null));
        }
        if (challenge.status() != OtpStatus.CREATED && !recoveringFromVerifyLock) {
            return Mono.just(replacementResult(
                    challenge.otpChallengeId(),
                    null,
                    null,
                    OtpReplacementOutcome.NOT_REPLACEABLE,
                    null,
                    0,
                    command.resendCooldownSeconds(),
                    remainingResendAttempts(challenge.maxResendAttempts(), challenge.resendAttemptCount()),
                    null));
        }

        Integer retryAfterSeconds = recoveringFromVerifyLock
                ? null
                : cooldownRetryAfterSeconds(
                        challenge.lastSentAt(), command.resendCooldownSeconds(), now);
        if (retryAfterSeconds != null) {
            return Mono.just(replacementResult(
                    challenge.otpChallengeId(),
                    null,
                    null,
                    OtpReplacementOutcome.COOLDOWN_ACTIVE,
                    null,
                    0,
                    command.resendCooldownSeconds(),
                    remainingResendAttempts(challenge.maxResendAttempts(), challenge.resendAttemptCount()),
                    retryAfterSeconds));
        }
        if (challenge.resendAttemptCount() >= challenge.maxResendAttempts()) {
            log.warn("Max resend attempts reached for OTP challenge {} ({}).", challenge.otpChallengeId(), challenge.resendAttemptCount());
            return Mono.just(replacementResult(
                    challenge.otpChallengeId(),
                    null,
                    null,
                    OtpReplacementOutcome.MAX_RESEND_EXCEEDED,
                    null,
                    0,
                    command.resendCooldownSeconds(),
                    0,
                    null));
        }

        String rawCode = otpCodeGenerator.generate(command.length(), command.numericOnly());
        String newChallengeId = otpChallengeIdGenerator.generate();
        String newCodeHash = otpCodeHasher.hash(rawCode);
        int updatedResendAttemptCount = challenge.resendAttemptCount() + 1;
        Instant expiresAt = now.plusSeconds(command.ttlSeconds());

        OtpChallenge invalidatedChallenge = copyChallenge(
                challenge,
                challenge.otpChallengeId(),
                challenge.codeHash(),
                OtpStatus.INVALIDATED,
                challenge.failedAttemptCount(),
                challenge.resendAttemptCount(),
                challenge.createdAt(),
                challenge.expiresAt(),
                challenge.lastSentAt(),
                now,
                challenge.verifiedAt());
        OtpChallenge replacementChallenge = copyChallenge(
                challenge,
                newChallengeId,
                newCodeHash,
                OtpStatus.CREATED,
                0,
                updatedResendAttemptCount,
                now,
                expiresAt,
                now,
                null,
                null);

        return otpChallengeStatePort.update(invalidatedChallenge)
                .then(otpChallengeStatePort.save(replacementChallenge))
                .thenReturn(replacementResult(
                        challenge.otpChallengeId(),
                        replacementChallenge.otpChallengeId(),
                        rawCode,
                        OtpReplacementOutcome.REPLACED,
                        replacementChallenge.expiresAt(),
                        command.ttlSeconds(),
                        command.resendCooldownSeconds(),
                        remainingResendAttempts(challenge.maxResendAttempts(), updatedResendAttemptCount),
                        null));
    }

    private VerifyOtpChallengeResult result(String otpChallengeId, OtpVerificationOutcome outcome,
            int remainingAttempts) {
        return new VerifyOtpChallengeResult(otpChallengeId, outcome, remainingAttempts, null);
    }

    private ReplaceOtpChallengeResult replacementResult(
            String previousOtpChallengeId,
            String newOtpChallengeId,
            String rawCode,
            OtpReplacementOutcome outcome,
            Instant expiresAt,
            int expiresInSeconds,
            int resendCooldownSeconds,
            int remainingResendAttempts,
            Integer retryAfterSeconds) {
        return new ReplaceOtpChallengeResult(
                previousOtpChallengeId,
                newOtpChallengeId,
                rawCode,
                outcome,
                expiresAt,
                expiresInSeconds,
                resendCooldownSeconds,
                remainingResendAttempts,
                retryAfterSeconds);
    }

    private int remainingAttempts(int maxVerifyAttempts, int failedAttemptCount) {
        return Math.max(0, maxVerifyAttempts - failedAttemptCount);
    }

    private int remainingResendAttempts(int maxResendAttempts, int resendAttemptCount) {
        return Math.max(0, maxResendAttempts - resendAttemptCount);
    }

    private Integer cooldownRetryAfterSeconds(Instant lastSentAt, int resendCooldownSeconds, Instant now) {
        if (lastSentAt == null || resendCooldownSeconds <= 0) {
            return null;
        }

        Instant availableAt = lastSentAt.plusSeconds(resendCooldownSeconds);
        if (!availableAt.isAfter(now)) {
            return null;
        }

        return Math.toIntExact(Math.max(1L, Duration.between(now, availableAt).getSeconds()));
    }

    private void validateCreateCommand(CreateOtpChallengeCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("Create OTP challenge command must not be null");
        }
        if (command.scenario() == null) {
            throw new IllegalArgumentException("OTP scenario must not be null");
        }
        if (command.workflowRef() == null || command.workflowRef().isBlank()) {
            throw new IllegalArgumentException("OTP workflow reference must not be blank");
        }
        if (command.channel() == null) {
            throw new IllegalArgumentException("OTP channel must not be null");
        }
        if (command.destinationHash() == null || command.destinationHash().isBlank()) {
            throw new IllegalArgumentException("OTP destination hash must not be blank");
        }
        if (command.length() <= 0) {
            throw new IllegalArgumentException("OTP length must be greater than zero");
        }
        if (command.ttlSeconds() <= 0) {
            throw new IllegalArgumentException("OTP TTL seconds must be greater than zero");
        }
        if (command.maxVerifyAttempts() <= 0) {
            throw new IllegalArgumentException("OTP max verify attempts must be greater than zero");
        }
        if (command.maxResendAttempts() < 0) {
            throw new IllegalArgumentException("OTP max resend attempts must not be negative");
        }
    }

    private OtpChallenge copyChallenge(
            OtpChallenge challenge,
            OtpStatus status,
            int failedAttemptCount,
            Instant invalidatedAt,
            Instant verifiedAt) {
        return copyChallenge(
                challenge,
                challenge.otpChallengeId(),
                challenge.codeHash(),
                status,
                failedAttemptCount,
                challenge.resendAttemptCount(),
                challenge.createdAt(),
                challenge.expiresAt(),
                challenge.lastSentAt(),
                invalidatedAt,
                verifiedAt);
    }

    private OtpChallenge copyChallenge(
            OtpChallenge challenge,
            String otpChallengeId,
            String codeHash,
            OtpStatus status,
            int failedAttemptCount,
            int resendAttemptCount,
            Instant createdAt,
            Instant expiresAt,
            Instant lastSentAt,
            Instant invalidatedAt,
            Instant verifiedAt) {
        return new OtpChallenge(
                otpChallengeId,
                challenge.scenario(),
                challenge.workflowRef(),
                challenge.channel(),
                challenge.destinationHash(),
                codeHash,
                status,
                failedAttemptCount,
                challenge.maxVerifyAttempts(),
                resendAttemptCount,
                challenge.maxResendAttempts(),
                createdAt,
                expiresAt,
                lastSentAt,
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

    private void validateReplaceCommand(ReplaceOtpChallengeCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("Replace OTP challenge command must not be null");
        }
        if (command.currentOtpChallengeId() == null || command.currentOtpChallengeId().isBlank()) {
            throw new IllegalArgumentException("Current OTP challenge ID must not be blank");
        }
        if (command.length() <= 0) {
            throw new IllegalArgumentException("OTP length must be greater than zero");
        }
        if (command.ttlSeconds() <= 0) {
            throw new IllegalArgumentException("OTP TTL seconds must be greater than zero");
        }
        if (command.resendCooldownSeconds() < 0) {
            throw new IllegalArgumentException("OTP resend cooldown seconds must not be negative");
        }
    }

    private void validateInvalidateCommand(InvalidateOtpChallengeCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("Invalidate OTP challenge command must not be null");
        }
        if (command.otpChallengeId() == null || command.otpChallengeId().isBlank()) {
            throw new IllegalArgumentException("OTP challenge ID must not be blank");
        }
    }

    private void requireCreateDependencies() {
        if (otpCodeGenerator == null || otpChallengeIdGenerator == null) {
            throw new IllegalStateException("OTP creation dependencies are not configured");
        }
    }

    private void requireReplacementDependencies() {
        if (otpCodeGenerator == null || otpChallengeIdGenerator == null) {
            throw new IllegalStateException("OTP replacement dependencies are not configured");
        }
    }
}