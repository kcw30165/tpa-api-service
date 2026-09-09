package com.bct.ngtpa.apiservice.application.usecase.auth.usernamerecovery.idempotency.start;

import com.bct.ngtpa.apiservice.application.idempotency.ProtectedReplayReference;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Objects;

/** Generates opaque START replay references with 256 bits of entropy. */
public final class SecureUsernameRecoveryStartProtectedReplayReferenceGenerator
    implements UsernameRecoveryStartProtectedReplayReferenceGenerator {
  private static final int ENTROPY_BYTES = 32;
  private final SecureRandom random;

  public SecureUsernameRecoveryStartProtectedReplayReferenceGenerator() {
    this(new SecureRandom());
  }

  SecureUsernameRecoveryStartProtectedReplayReferenceGenerator(SecureRandom random) {
    this.random = Objects.requireNonNull(random, "random must not be null");
  }

  @Override
  public ProtectedReplayReference generate() {
    byte[] bytes = new byte[ENTROPY_BYTES];
    random.nextBytes(bytes);
    return new ProtectedReplayReference(
        Base64.getUrlEncoder().withoutPadding().encodeToString(bytes));
  }
}
