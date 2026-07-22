package com.bct.ngtpa.apiservice.application.port.out.authflow;

import reactor.core.publisher.Mono;

/** Scenario-neutral auth-flow message delivery boundary. */
public interface AuthFlowMessagePort<C> {
    Mono<Boolean> deliver(
            C channel,
            String recipient,
            String code,
            int ttlSeconds,
            String acceptLanguage,
            String correlationId);
}
