package com.bct.ngtpa.apiservice.application.dto.session.authenticated;

import java.security.SecureRandom;
import java.util.HexFormat;

public final class SecureAuthenticatedSessionRevisionGenerator
    implements AuthenticatedSessionRevisionGenerator {

  private final SecureRandom secureRandom = new SecureRandom();

  @Override
  public SessionRevision generate() {
    byte[] entropy = new byte[16];
    secureRandom.nextBytes(entropy);
    return new SessionRevision(HexFormat.of().formatHex(entropy));
  }
}
