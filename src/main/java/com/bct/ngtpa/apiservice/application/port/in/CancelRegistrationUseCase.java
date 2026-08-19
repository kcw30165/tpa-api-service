package com.bct.ngtpa.apiservice.application.port.in;

import com.bct.ngtpa.apiservice.application.usecase.auth.registration.cancel.CancelRegistrationCommand;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.cancel.CancelRegistrationResult;
import reactor.core.publisher.Mono;

public interface CancelRegistrationUseCase {
  Mono<CancelRegistrationResult> cancel(CancelRegistrationCommand command);
}
