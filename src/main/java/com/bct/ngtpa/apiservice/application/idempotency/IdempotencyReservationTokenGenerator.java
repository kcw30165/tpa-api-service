package com.bct.ngtpa.apiservice.application.idempotency;

/** Generates an unpredictable ownership token before atomic reservation. */
public interface IdempotencyReservationTokenGenerator {
  IdempotencyReservationToken generate();
}
