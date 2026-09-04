package com.bct.ngtpa.apiservice.application.usecase.auth.registration.idempotency;

import com.bct.ngtpa.apiservice.application.idempotency.ProtectedReplayReference;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Objects;

public final class SecureRegistrationStartProtectedReplayReferenceGenerator
    implements RegistrationStartProtectedReplayReferenceGenerator {
  private final SecureRandom random;

  public SecureRegistrationStartProtectedReplayReferenceGenerator() {
    this(new SecureRandom());
  }

  SecureRegistrationStartProtectedReplayReferenceGenerator(SecureRandom random) {
    this.random = Objects.requireNonNull(random, "random must not be null");
  }

  @Override
  public ProtectedReplayReference generate() {
    byte[] entropy = new byte[32];
    random.nextBytes(entropy);
    return new ProtectedReplayReference(
        Base64.getUrlEncoder().withoutPadding().encodeToString(entropy));
  }
}
