package com.bct.ngtpa.apiservice.application.port.out;

import com.bct.ngtpa.apiservice.application.usecase.auth.registration.complete.RegistrationCompletionCommand;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.complete.RegistrationCompletionResult;
import reactor.core.publisher.Mono;

public interface RegistrationCompletionPort {
  Mono<RegistrationCompletionResult> complete(RegistrationCompletionCommand command);
}
