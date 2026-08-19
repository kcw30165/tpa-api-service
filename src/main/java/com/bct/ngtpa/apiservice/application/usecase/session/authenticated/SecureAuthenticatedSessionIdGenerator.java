package com.bct.ngtpa.apiservice.application.usecase.session.authenticated;

import com.bct.ngtpa.apiservice.application.dto.session.authenticated.AuthenticatedSessionId;
import java.security.SecureRandom;
import java.util.*;

public final class SecureAuthenticatedSessionIdGenerator
    implements AuthenticatedSessionIdGenerator {
  private final SecureRandom random;
  private final int entropyBytes;

  public SecureAuthenticatedSessionIdGenerator() {
    this(new SecureRandom(), 24);
  }

  SecureAuthenticatedSessionIdGenerator(SecureRandom random, int entropyBytes) {
    this.random = Objects.requireNonNull(random);
    if (entropyBytes < 16) throw new IllegalArgumentException("entropyBytes must be at least 16");
    this.entropyBytes = entropyBytes;
  }

  public AuthenticatedSessionId generate() {
    byte[] value = new byte[entropyBytes];
    random.nextBytes(value);
    return new AuthenticatedSessionId(
        "sid_" + Base64.getUrlEncoder().withoutPadding().encodeToString(value));
  }
}
