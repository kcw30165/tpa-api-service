package com.bct.ngtpa.apiservice.application.usecase.session.authflow;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

public class Sha256CsrfTokenHasher implements CsrfTokenHasher {
  @Override
  public String hash(String rawToken) {
    if (rawToken == null || rawToken.isBlank()) {
      throw new IllegalArgumentException("CSRF token must not be blank");
    }
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256")
                  .digest(rawToken.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("CSRF hasher unavailable", exception);
    }
  }

  @Override
  public boolean matches(String rawToken, String tokenHash) {
    if (rawToken == null || rawToken.isBlank() || tokenHash == null || tokenHash.isBlank()) {
      return false;
    }
    return MessageDigest.isEqual(
        hash(rawToken).getBytes(StandardCharsets.UTF_8),
        tokenHash.getBytes(StandardCharsets.UTF_8));
  }
}
