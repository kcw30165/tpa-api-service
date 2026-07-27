package com.bct.ngtpa.apiservice.application.port.in;

import com.bct.ngtpa.apiservice.application.usecase.auth.login.cancel.*;
import reactor.core.publisher.Mono;

@FunctionalInterface
public interface CancelLoginUseCase {
    Mono<CancelLoginResult> cancel(CancelLoginCommand command);
}
