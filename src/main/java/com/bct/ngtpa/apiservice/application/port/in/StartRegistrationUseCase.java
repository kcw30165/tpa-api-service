package com.bct.ngtpa.apiservice.application.port.in;

import com.bct.ngtpa.apiservice.application.usecase.auth.registration.StartRegistrationCommand;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.StartRegistrationResult;
import reactor.core.publisher.Mono;

public interface StartRegistrationUseCase {
    Mono<StartRegistrationResult> start(StartRegistrationCommand command);
}
