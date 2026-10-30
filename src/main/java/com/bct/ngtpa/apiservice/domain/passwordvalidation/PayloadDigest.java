package com.bct.ngtpa.apiservice.domain.passwordvalidation;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

public record PayloadDigest(int version, String sha256) {
  public PayloadDigest {
    if (version <= 0 || sha256 == null || !sha256.matches("[0-9a-f]{64}")) {
      throw new IllegalArgumentException("Invalid payload digest");
    }
  }

  public boolean matches(PayloadDigest other) {
    return other != null
        && version == other.version
        && MessageDigest.isEqual(
            sha256.getBytes(StandardCharsets.US_ASCII),
            other.sha256.getBytes(StandardCharsets.US_ASCII));
  }

  @Override
  public String toString() {
    return "PayloadDigest[version=" + version + ", sha256=<redacted>]";
  }
}
