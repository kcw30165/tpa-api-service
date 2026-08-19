package com.bct.ngtpa.apiservice.application.port.in;

import com.bct.ngtpa.apiservice.application.usecase.auth.registration.complete.CompleteRegistrationCommand;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.complete.CompleteRegistrationResult;
import reactor.core.publisher.Mono;

public interface CompleteRegistrationUseCase {
  Mono<CompleteRegistrationResult> complete(CompleteRegistrationCommand command);
}
