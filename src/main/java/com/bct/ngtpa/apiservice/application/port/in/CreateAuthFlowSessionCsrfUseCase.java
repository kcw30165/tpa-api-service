package com.bct.ngtpa.apiservice.application.port.in;

import com.bct.ngtpa.apiservice.application.usecase.session.authflow.CreateAuthFlowSessionCsrfCommand;
import com.bct.ngtpa.apiservice.application.usecase.session.authflow.CreateAuthFlowSessionCsrfResult;
import reactor.core.publisher.Mono;

/** Narrow application capability for establishing a session-to-CSRF binding. */
@FunctionalInterface
public interface CreateAuthFlowSessionCsrfUseCase {
  Mono<CreateAuthFlowSessionCsrfResult> createForSession(CreateAuthFlowSessionCsrfCommand command);
}
