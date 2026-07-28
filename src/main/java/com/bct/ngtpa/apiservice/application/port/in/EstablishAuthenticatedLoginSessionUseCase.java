package com.bct.ngtpa.apiservice.application.port.in;

import com.bct.ngtpa.apiservice.application.usecase.auth.login.session.EstablishAuthenticatedLoginSessionCommand;
import com.bct.ngtpa.apiservice.application.usecase.auth.login.session.EstablishAuthenticatedLoginSessionResult;
import reactor.core.publisher.Mono;

@FunctionalInterface
public interface EstablishAuthenticatedLoginSessionUseCase {
    Mono<EstablishAuthenticatedLoginSessionResult> establish(
            EstablishAuthenticatedLoginSessionCommand command);
}
