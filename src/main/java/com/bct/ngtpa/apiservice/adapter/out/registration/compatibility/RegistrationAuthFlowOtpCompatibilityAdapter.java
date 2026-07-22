package com.bct.ngtpa.apiservice.adapter.out.registration.compatibility;

import com.bct.ngtpa.apiservice.application.dto.auth.registration.RegistrationContactType;
import com.bct.ngtpa.apiservice.application.port.out.RegistrationOtpPort;
import com.bct.ngtpa.apiservice.application.port.out.authflow.AuthFlowOtpPort;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.RegistrationOtpCreationResult;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.otp.RegistrationOtpVerificationResult;
import java.util.Objects;
import reactor.core.publisher.Mono;

/** Temporary one-way bridge retained until 0043-002-002 migrates registration. */
public final class RegistrationAuthFlowOtpCompatibilityAdapter implements
        AuthFlowOtpPort<RegistrationContactType, RegistrationOtpCreationResult, RegistrationOtpVerificationResult> {
    private final RegistrationOtpPort delegate;

    public RegistrationAuthFlowOtpCompatibilityAdapter(RegistrationOtpPort delegate) {
        this.delegate = Objects.requireNonNull(delegate, "delegate must not be null");
    }

    @Override
    public Mono<RegistrationOtpCreationResult> create(RegistrationContactType channel, String destination) {
        return delegate.createOtp(channel, destination);
    }

    @Override
    public Mono<RegistrationOtpCreationResult> resend(
            String activeChallengeId, RegistrationContactType channel, String destination) {
        return delegate.resendOtp(activeChallengeId, channel, destination);
    }

    @Override
    public Mono<RegistrationOtpVerificationResult> verify(String activeChallengeId, String submittedCode) {
        return delegate.verifyOtp(activeChallengeId, submittedCode);
    }
}
