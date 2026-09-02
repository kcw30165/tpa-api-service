package com.bct.ngtpa.apiservice.application.idempotency;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Objects;

/** Secure URL-safe reservation-token generator with 256 bits of entropy. */
public final class SecureIdempotencyReservationTokenGenerator
    implements IdempotencyReservationTokenGenerator {
  private static final int TOKEN_BYTES = 32;
  private final SecureRandom secureRandom;

  public SecureIdempotencyReservationTokenGenerator() {
    this(new SecureRandom());
  }

  SecureIdempotencyReservationTokenGenerator(SecureRandom secureRandom) {
    this.secureRandom = Objects.requireNonNull(secureRandom, "secure random must not be null");
  }

  @Override
  public IdempotencyReservationToken generate() {
    byte[] bytes = new byte[TOKEN_BYTES];
    secureRandom.nextBytes(bytes);
    return new IdempotencyReservationToken(
        Base64.getUrlEncoder().withoutPadding().encodeToString(bytes));
  }
}
