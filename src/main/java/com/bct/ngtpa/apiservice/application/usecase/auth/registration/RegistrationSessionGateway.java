package com.bct.ngtpa.apiservice.application.usecase.auth.registration;

import com.bct.ngtpa.apiservice.application.dto.auth.registration.context.RegistrationAuthFlowContext;

import com.bct.ngtpa.apiservice.application.dto.auth.registration.RegistrationContactType;
import reactor.core.publisher.Mono;

public interface RegistrationSessionGateway {
    Mono<String> createRegistrationSession(String maskedDestination, RegistrationContactType contactType);
    Mono<Void> updateOtpChallenge(String authFlowSessionId, String activeOtpChallengeId, RegistrationContactType contactType, String maskedDestination);


    default Mono<String> createRegistrationSession(
            String maskedDestination,
            RegistrationContactType contactType,
            RegistrationAuthFlowContext registrationContext) {
        return createRegistrationSession(maskedDestination, contactType);
    }
}
