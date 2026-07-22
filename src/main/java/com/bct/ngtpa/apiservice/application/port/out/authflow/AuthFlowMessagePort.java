package com.bct.ngtpa.apiservice.application.port.out.authflow;

import reactor.core.publisher.Mono;

/** Scenario-neutral auth-flow OTP message-delivery boundary. */
public interface AuthFlowMessagePort<C> {
    Mono<Boolean> deliverOtp(
            C channel,
            String recipient,
            String code,
            int ttlSeconds,
            String acceptLanguage,
            String correlationId);
}
