package com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.idempotency.start;

import com.bct.ngtpa.apiservice.application.idempotency.ProtectedReplayReference;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Objects;

/** Generates opaque Start replay references with 256 bits of entropy. */
public final class SecureForgotPasswordStartProtectedReplayReferenceGenerator
    implements ForgotPasswordStartProtectedReplayReferenceGenerator {
  private static final int ENTROPY_BYTES = 32;
  private final SecureRandom random;

  public SecureForgotPasswordStartProtectedReplayReferenceGenerator() {
    this(new SecureRandom());
  }

  SecureForgotPasswordStartProtectedReplayReferenceGenerator(SecureRandom random) {
    this.random = Objects.requireNonNull(random, "random must not be null");
  }

  @Override
  public ProtectedReplayReference generate() {
    byte[] entropy = new byte[ENTROPY_BYTES];
    random.nextBytes(entropy);
    return new ProtectedReplayReference(
        Base64.getUrlEncoder().withoutPadding().encodeToString(entropy));
  }
}
