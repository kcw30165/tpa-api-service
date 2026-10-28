package com.bct.ngtpa.apiservice.application.usecase.passwordvalidation;

import com.bct.ngtpa.apiservice.domain.passwordvalidation.PayloadDigest;
import java.time.Instant;
import java.util.Objects;

record PasswordValidationGrant(
    String tokenDigest, PayloadDigest binding, Instant issuedAt, Instant expiresAt) {
  PasswordValidationGrant {
    if (tokenDigest == null || !tokenDigest.matches("[0-9a-f]{64}")) {
      throw new IllegalArgumentException("Invalid grant digest");
    }
    Objects.requireNonNull(binding);
    Objects.requireNonNull(issuedAt);
    if (!issuedAt.plusSeconds(300).equals(expiresAt)) {
      throw new IllegalArgumentException("Invalid grant lifetime");
    }
  }

  String encode() {
    return tokenDigest
        + "|"
        + binding.version()
        + "|"
        + binding.sha256()
        + "|"
        + issuedAt.toEpochMilli()
        + "|"
        + expiresAt.toEpochMilli();
  }

  static PasswordValidationGrant decode(String encoded) {
    if (encoded == null || encoded.length() > 256) return null;
    try {
      String[] parts = encoded.split("\\|", -1);
      if (parts.length != 5) return null;
      return new PasswordValidationGrant(
          parts[0],
          new PayloadDigest(Integer.parseInt(parts[1]), parts[2]),
          Instant.ofEpochMilli(Long.parseLong(parts[3])),
          Instant.ofEpochMilli(Long.parseLong(parts[4])));
    } catch (IllegalArgumentException | java.time.DateTimeException exception) {
      return null;
    }
  }

  @Override
  public String toString() {
    return "PasswordValidationGrant[<redacted>]";
  }
}
