package com.bct.ngtpa.apiservice.application.port.out;

import com.bct.ngtpa.apiservice.application.dto.auth.registration.RegistrationContactType;
import com.bct.ngtpa.apiservice.application.port.out.authflow.AuthFlowMessagePort;
import reactor.core.publisher.Mono;

/** Registration typed compatibility alias for shared auth-flow OTP delivery. */
public interface RegistrationMessagePort extends AuthFlowMessagePort<RegistrationContactType> {
    Mono<Boolean> deliverRegistrationOtp(
            RegistrationContactType contactType,
            String recipient,
            String otpCode,
            int ttlSeconds,
            String acceptLanguage,
            String correlationId);

    @Override
    default Mono<Boolean> deliverOtp(
            RegistrationContactType channel,
            String recipient,
            String code,
            int ttlSeconds,
            String acceptLanguage,
            String correlationId) {
        return deliverRegistrationOtp(
                channel, recipient, code, ttlSeconds, acceptLanguage, correlationId);
    }
}
