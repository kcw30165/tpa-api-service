package com.bct.ngtpa.apiservice.application.port.out;

import com.bct.ngtpa.apiservice.application.dto.auth.registration.RegistrationContactType;
import reactor.core.publisher.Mono;

/** Registration typed compatibility alias for shared auth-flow OTP delivery. */
public interface RegistrationMessagePort {
  Mono<Boolean> deliverRegistrationOtp(
      RegistrationContactType contactType,
      String recipient,
      String otpCode,
      int ttlSeconds,
      String acceptLanguage,
      String correlationId);
}
