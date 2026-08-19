package com.bct.ngtpa.apiservice.application.port.out;

import com.bct.ngtpa.apiservice.application.dto.auth.registration.RegistrationContactType;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.RegistrationOtpCreationResult;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.otp.RegistrationOtpVerificationResult;
import reactor.core.publisher.Mono;

/**
 * Registration typed compatibility alias for the shared auth-flow OTP contract. Registration
 * business code may migrate incrementally without duplicating the OTP challenge engine. Remove only
 * after all registration test doubles and composition roots use the shared typed contract directly.
 */
public interface RegistrationOtpPort {

  Mono<RegistrationOtpCreationResult> createOtp(
      RegistrationContactType contactType, String destination);

  default Mono<RegistrationOtpCreationResult> resendOtp(
      String activeOtpChallengeId, RegistrationContactType contactType, String destination) {
    return createOtp(contactType, destination);
  }

  default Mono<RegistrationOtpVerificationResult> verifyOtp(
      String activeOtpChallengeId, String submittedOtpCode) {
    return Mono.just(RegistrationOtpVerificationResult.invalid(null));
  }
}
