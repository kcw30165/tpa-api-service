package com.bct.ngtpa.apiservice.application.port.out;

import com.bct.ngtpa.apiservice.application.dto.auth.registration.RegistrationContactType;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.RegistrationOtpCreationResult;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.otp.RegistrationOtpVerificationResult;
import reactor.core.publisher.Mono;

public interface RegistrationOtpPort {
    Mono<RegistrationOtpCreationResult> createOtp(RegistrationContactType contactType, String destination);


    default Mono<RegistrationOtpCreationResult> resendOtp(
            String activeOtpChallengeId,
            RegistrationContactType contactType,
            String destination) {
        return createOtp(contactType, destination);
    }


    default Mono<RegistrationOtpVerificationResult> verifyOtp(String activeOtpChallengeId, String submittedOtpCode) {
        return Mono.just(RegistrationOtpVerificationResult.invalid(null));
    }
}
