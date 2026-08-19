package com.bct.ngtpa.apiservice.application.port.out;

import com.bct.ngtpa.apiservice.application.dto.auth.login.idempotency.LoginIdempotencyKey;
import com.bct.ngtpa.apiservice.application.dto.auth.login.otp.verify.*;
import reactor.core.publisher.Mono;

public interface LoginOtpVerifyPersistencePort {
  default Mono<LoginOtpVerifyReservationResult> reserveOrLoad(
      ReserveLoginOtpVerifyCommand command) {
    var reservation =
        new LoginOtpVerifyReservation(
            command.requestHash(),
            LoginOtpVerifyReservationStatus.PROCESSING,
            command.proposedHandoff(),
            command.proposedOperation(),
            null,
            null,
            null,
            command.now(),
            command.expiresAt());
    return Mono.just(
        new LoginOtpVerifyReservationResult(LoginOtpVerifyReservationOutcome.STARTED, reservation));
  }

  default Mono<LoginOtpVerifyReservationResult> loadReservation(
      LoginIdempotencyKey key, String requestHash) {
    return Mono.empty();
  }

  default Mono<LoginOtpConsumptionProof> loadConsumptionProof(
      LoginIdempotencyKey key, String requestHash) {
    return Mono.empty();
  }

  default Mono<LoginOtpVerifyReservation> recordVerificationCommitted(
      RecordLoginOtpVerifyCommittedCommand command) {
    return Mono.empty();
  }

  default Mono<LoginOtpVerifyReservation> recordConclusiveFailure(
      LoginIdempotencyKey key,
      String requestHash,
      LoginOtpVerifyPersistenceOutcome outcome,
      Integer remainingAttempts) {
    return Mono.empty();
  }

  default Mono<LoginOtpVerifyReservation> recordUnknownOutcome(
      LoginIdempotencyKey key, String requestHash) {
    return Mono.empty();
  }

  default Mono<LoginOtpVerifyReservation> complete(LoginIdempotencyKey key, String requestHash) {
    return Mono.empty();
  }

  Mono<LoginOtpVerifyPersistenceResult> verify(RevisionedLoginOtpVerifyCommand command);
}
