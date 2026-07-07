package com.bct.ngtpa.apiservice.application.port.out;

import reactor.core.publisher.Mono;

public interface RegistrationCsrPort {
    Mono<String> bindCsrfToken(String authFlowSessionId);
}
