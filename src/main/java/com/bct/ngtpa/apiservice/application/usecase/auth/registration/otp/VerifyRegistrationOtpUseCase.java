package com.bct.ngtpa.apiservice.application.usecase.auth.registration.otp;

import reactor.core.publisher.Mono;

public interface VerifyRegistrationOtpUseCase {
    Mono<VerifyRegistrationOtpResult> verify(VerifyRegistrationOtpCommand command);
}
