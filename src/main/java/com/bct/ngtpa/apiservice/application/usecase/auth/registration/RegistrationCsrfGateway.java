package com.bct.ngtpa.apiservice.application.usecase.auth.registration;

import reactor.core.publisher.Mono;

public interface RegistrationCsrfGateway {
    Mono<String> bindCsrfToken(String authFlowSessionId);
}
