package com.bct.ngtpa.apiservice.application.port.out;

import com.bct.ngtpa.apiservice.application.dto.auth.registration.RegistrationContactType;
import com.bct.ngtpa.apiservice.application.dto.auth.registration.context.RegistrationAuthFlowContext;
import reactor.core.publisher.Mono;

public interface RegistrationSessionPort {
  Mono<String> createRegistrationSession(
      String maskedDestination, RegistrationContactType contactType);

  Mono<Void> updateOtpChallenge(
      String authFlowSessionId,
      String activeOtpChallengeId,
      RegistrationContactType contactType,
      String maskedDestination);

  default Mono<String> createRegistrationSession(
      String maskedDestination,
      RegistrationContactType contactType,
      RegistrationAuthFlowContext registrationContext) {
    return createRegistrationSession(maskedDestination, contactType);
  }

  default Mono<Void> markOtpVerified(String authFlowSessionId) {
    return Mono.empty();
  }
}
