package com.bct.ngtpa.apiservice.application.usecase.auth.registration.otp;

import reactor.core.publisher.Mono;

public interface ResendRegistrationOtpUseCase {
    Mono<ResendRegistrationOtpResult> resend(ResendRegistrationOtpCommand command);
}
