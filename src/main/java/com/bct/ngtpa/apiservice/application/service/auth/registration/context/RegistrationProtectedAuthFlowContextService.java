package com.bct.ngtpa.apiservice.application.service.auth.registration.context;

import reactor.core.publisher.Mono;

public interface RegistrationProtectedAuthFlowContextService {
    Mono<RegistrationProtectedAuthFlowContextResult> load(RegistrationProtectedAuthFlowContextCommand command);
}
