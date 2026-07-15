package com.bct.ngtpa.apiservice.adapter.out.registration;

import com.bct.ngtpa.apiservice.application.dto.security.ratelimit.RateLimitBucket;
import com.bct.ngtpa.apiservice.application.dto.security.ratelimit.RateLimitCommand;
import com.bct.ngtpa.apiservice.application.dto.security.ratelimit.RateLimitStatus;
import com.bct.ngtpa.apiservice.application.port.out.RateLimitPort;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.otp.RegistrationOtpSendRateLimitedException;
import com.bct.ngtpa.apiservice.application.dto.auth.RegistrationAuthWorkflowPolicy;
import com.bct.ngtpa.apiservice.application.dto.auth.OtpPolicy;
import com.bct.ngtpa.apiservice.application.dto.auth.otp.CreateOtpChallengeCommand;
import com.bct.ngtpa.apiservice.application.dto.auth.otp.OtpChannel;
import com.bct.ngtpa.apiservice.application.dto.auth.otp.OtpScenario;
import com.bct.ngtpa.apiservice.application.dto.auth.registration.RegistrationContactType;
import com.bct.ngtpa.apiservice.application.port.out.AuthWorkflowPolicyPort;
import com.bct.ngtpa.apiservice.application.port.out.OtpChallengePort;
import com.bct.ngtpa.apiservice.application.port.out.RegistrationOtpPort;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.RegistrationOtpCreationResult;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Objects;
import java.util.Locale;
import reactor.core.publisher.Mono;
import com.bct.ngtpa.apiservice.application.dto.auth.otp.VerifyOtpChallengeCommand;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.otp.RegistrationOtpVerificationResult;
import com.bct.ngtpa.apiservice.application.dto.auth.otp.OtpReplacementOutcome;
import com.bct.ngtpa.apiservice.application.dto.auth.otp.ReplaceOtpChallengeCommand;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.otp.RegistrationOtpResendRejectedException;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.otp.ResendRegistrationOtpResultStatus;

public class RegistrationOtpChallengeAdapter implements RegistrationOtpPort {
    private final OtpChallengePort otpChallengePort;
    private final AuthWorkflowPolicyPort authWorkflowPolicyPort;
    private final Clock clock;
    private final RateLimitPort rateLimitPort;

    public RegistrationOtpChallengeAdapter(
            OtpChallengePort otpChallengePort,
            AuthWorkflowPolicyPort authWorkflowPolicyPort,
            Clock clock) {
        this(otpChallengePort, authWorkflowPolicyPort, clock, null);
    }

    public RegistrationOtpChallengeAdapter(
            OtpChallengePort otpChallengePort,
            AuthWorkflowPolicyPort authWorkflowPolicyPort,
            Clock clock,
            RateLimitPort rateLimitPort) {
        this.otpChallengePort = Objects.requireNonNull(otpChallengePort, "otpChallengePort must not be null");
        this.authWorkflowPolicyPort = Objects.requireNonNull(authWorkflowPolicyPort, "authWorkflowPolicyPort must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        this.rateLimitPort = rateLimitPort;
    }

    @Override
    public Mono<RegistrationOtpCreationResult> createOtp(
            RegistrationContactType contactType,
            String destination) {
        OtpPolicy policy = otpPolicy();
        OtpChannel channel = toOtpChannel(contactType);
        String destinationHash = destinationHash(channel, destination);

        var command = new CreateOtpChallengeCommand(
                OtpScenario.REGISTRATION,
                "registration:" + destinationHash,
                channel,
                destinationHash,
                policy.length(),
                policy.numericOnly(),
                policy.ttlSeconds(),
                policy.maxVerifyAttempts(),
                policy.maxResendAttempts(),
                Instant.now(clock));

        return enforceOtpSendRateLimit(channel, destinationHash)
                .then(otpChallengePort.createChallenge(command))
                .map(result -> new RegistrationOtpCreationResult(
                        result.otpChallengeId(),
                        result.rawCode(),
                        result.expiresInSeconds(),
                        policy.resendCooldownSeconds(),
                        policy.maxResendAttempts()));
    }





    @Override
    public Mono<RegistrationOtpCreationResult> resendOtp(
            String activeOtpChallengeId,
            RegistrationContactType contactType,
            String destination) {
        OtpPolicy policy = otpPolicy();
        OtpChannel channel = toOtpChannel(contactType);
        String destinationHash = destinationHash(channel, destination);
        return enforceOtpSendRateLimit(channel, destinationHash)
                .then(otpChallengePort.replaceChallenge(new ReplaceOtpChallengeCommand(
                        activeOtpChallengeId,
                        policy.length(),
                        policy.numericOnly(),
                        policy.ttlSeconds(),
                        policy.resendCooldownSeconds(),
                        Instant.now(clock))))
                .flatMap(result -> switch (result.outcome()) {
                    case REPLACED -> Mono.just(new RegistrationOtpCreationResult(
                            result.newOtpChallengeId(),
                            result.rawCode(),
                            result.expiresInSeconds(),
                            result.resendCooldownSeconds(),
                            result.remainingResendAttempts()));
                    case COOLDOWN_ACTIVE -> Mono.error(new RegistrationOtpResendRejectedException(
                            ResendRegistrationOtpResultStatus.COOLDOWN_ACTIVE,
                            result.retryAfterSeconds(),
                            result.remainingResendAttempts()));
                    case MAX_RESEND_EXCEEDED -> Mono.error(new RegistrationOtpResendRejectedException(
                            ResendRegistrationOtpResultStatus.MAX_RESEND_ATTEMPTS_REACHED,
                            result.retryAfterSeconds(),
                            result.remainingResendAttempts()));
                    case EXPIRED, LOCKED, INVALIDATED, ALREADY_VERIFIED, NOT_FOUND, NOT_REPLACEABLE ->
                            Mono.error(new RegistrationOtpResendRejectedException(
                                    ResendRegistrationOtpResultStatus.INVALID_AUTH_FLOW,
                                    result.retryAfterSeconds(),
                                    result.remainingResendAttempts()));
                });
    }

    @Override
    public Mono<RegistrationOtpVerificationResult> verifyOtp(String activeOtpChallengeId, String submittedOtpCode) {
        return otpChallengePort.verifyChallenge(new VerifyOtpChallengeCommand(
                        activeOtpChallengeId,
                        submittedOtpCode,
                        Instant.now(clock)))
                .map(result -> switch (result.outcome()) {
                    case VALID -> RegistrationOtpVerificationResult.verified();
                    case EXPIRED -> RegistrationOtpVerificationResult.expired();
                    case LOCKED -> RegistrationOtpVerificationResult.locked();
                    case INVALID, INVALIDATED, NOT_FOUND, ALREADY_VERIFIED ->
                            RegistrationOtpVerificationResult.invalid(result.remainingAttempts());
                });
    }

    private Mono<Void> enforceOtpSendRateLimit(OtpChannel channel, String destinationHash) {
        RegistrationAuthWorkflowPolicy workflowPolicy = authWorkflowPolicyPort.registrationPolicy();
        var rule = workflowPolicy == null || workflowPolicy.rateLimit() == null
                ? null : workflowPolicy.rateLimit().otpSendByDestinationHash();
        if (rateLimitPort == null || rule == null || !rule.enabled()) {
            return Mono.empty();
        }
        String bucketId = channel.name().toLowerCase(Locale.ROOT) + ":" + destinationHash;
        return rateLimitPort.checkAndConsume(new RateLimitCommand(
                        RateLimitBucket.OTP_SEND_BY_DESTINATION_HASH,
                        bucketId,
                        true,
                        rule.maxAttempts(),
                        rule.windowSeconds(),
                        null))
                .flatMap(result -> result.status() == RateLimitStatus.BLOCKED
                        ? Mono.error(new RegistrationOtpSendRateLimitedException(result.retryAfterSeconds()))
                        : Mono.empty());
    }

    private OtpPolicy otpPolicy() {
        RegistrationAuthWorkflowPolicy policy = authWorkflowPolicyPort.registrationPolicy();
        if (policy == null || policy.otp() == null) {
            throw new IllegalStateException("Registration OTP policy is not configured");
        }
        return policy.otp();
    }

    private OtpChannel toOtpChannel(RegistrationContactType contactType) {
        if (contactType == RegistrationContactType.MOBILE) {
            return OtpChannel.SMS;
        }
        return OtpChannel.EMAIL;
    }

    private String destinationHash(OtpChannel channel, String destination) {
        String normalized = destination == null ? "" : destination.trim();
        if (channel == OtpChannel.EMAIL) {
            normalized = normalized.toLowerCase(Locale.ROOT);
        }

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(channel.name().getBytes(StandardCharsets.UTF_8));
            digest.update((byte) 0);
            digest.update(normalized.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("Unable to hash OTP destination", exception);
        }
    }
}