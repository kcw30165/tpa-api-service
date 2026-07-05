package com.bct.ngtpa.apiservice.application.usecase.auth.registration;

import com.bct.ngtpa.apiservice.application.dto.auth.registration.RegistrationContactType;
import reactor.core.publisher.Mono;

public interface RegistrationOtpGateway {
    Mono<RegistrationOtpCreationResult> createOtp(RegistrationContactType contactType, String destination);


    default Mono<RegistrationOtpCreationResult> resendOtp(
            String activeOtpChallengeId,
            RegistrationContactType contactType,
            String destination) {
        return createOtp(contactType, destination);
    }
}
