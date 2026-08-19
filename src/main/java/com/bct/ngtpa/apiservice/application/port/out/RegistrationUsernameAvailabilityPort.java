package com.bct.ngtpa.apiservice.application.port.out;

import com.bct.ngtpa.apiservice.application.usecase.auth.registration.username.RegistrationUsernameAvailabilityCommand;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.username.RegistrationUsernameAvailabilityResult;
import reactor.core.publisher.Mono;

public interface RegistrationUsernameAvailabilityPort {
  Mono<RegistrationUsernameAvailabilityResult> checkAvailability(
      RegistrationUsernameAvailabilityCommand command);
}
