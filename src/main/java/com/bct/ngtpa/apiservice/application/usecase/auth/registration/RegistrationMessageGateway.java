package com.bct.ngtpa.apiservice.application.usecase.auth.registration;

import com.bct.ngtpa.apiservice.application.dto.auth.registration.RegistrationContactType;
import reactor.core.publisher.Mono;

public interface RegistrationMessageGateway {
    Mono<Boolean> deliverRegistrationOtp(RegistrationContactType contactType, String recipient, String otpCode, int ttlSeconds, String acceptLanguage, String correlationId);
}
