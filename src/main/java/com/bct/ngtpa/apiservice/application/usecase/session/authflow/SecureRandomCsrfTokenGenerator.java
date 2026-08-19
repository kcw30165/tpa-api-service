package com.bct.ngtpa.apiservice.application.usecase.session.authflow;

import java.security.SecureRandom;
import java.util.Base64;

public class SecureRandomCsrfTokenGenerator implements CsrfTokenGenerator {
  private static final int DEFAULT_ENTROPY_BYTES = 32;
  private final SecureRandom secureRandom = new SecureRandom();

  @Override
  public String generate() {
    byte[] bytes = new byte[DEFAULT_ENTROPY_BYTES];
    secureRandom.nextBytes(bytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }
}
