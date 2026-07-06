package com.bct.ngtpa.apiservice.adapter.out.registration;

import com.bct.ngtpa.apiservice.application.dto.auth.RegistrationAuthWorkflowPolicy;
import com.bct.ngtpa.apiservice.application.dto.auth.OtpPolicy;
import com.bct.ngtpa.apiservice.application.dto.auth.otp.CreateOtpChallengeCommand;
import com.bct.ngtpa.apiservice.application.dto.auth.otp.OtpChannel;
import com.bct.ngtpa.apiservice.application.dto.auth.otp.OtpScenario;
import com.bct.ngtpa.apiservice.application.dto.auth.registration.RegistrationContactType;
import com.bct.ngtpa.apiservice.application.port.out.AuthWorkflowPolicyPort;
import com.bct.ngtpa.apiservice.application.port.out.OtpChallengePort;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.RegistrationOtpCreationResult;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.RegistrationOtpGateway;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Objects;
import java.util.Locale;
import reactor.core.publisher.Mono;

public class RegistrationOtpChallengeGatewayAdapter implements RegistrationOtpGateway {
    private final OtpChallengePort otpChallengePort;
    private final AuthWorkflowPolicyPort authWorkflowPolicyPort;
    private final Clock clock;

    public RegistrationOtpChallengeGatewayAdapter(
            OtpChallengePort otpChallengePort,
            AuthWorkflowPolicyPort authWorkflowPolicyPort,
            Clock clock) {
        this.otpChallengePort = Objects.requireNonNull(otpChallengePort, "otpChallengePort must not be null");
        this.authWorkflowPolicyPort = Objects.requireNonNull(authWorkflowPolicyPort, "authWorkflowPolicyPort must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
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

        return otpChallengePort.createChallenge(command)
                .map(result -> new RegistrationOtpCreationResult(
                        result.otpChallengeId(),
                        result.rawCode(),
                        result.expiresInSeconds(),
                        policy.resendCooldownSeconds(),
                        policy.maxResendAttempts()));
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