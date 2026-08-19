package com.bct.ngtpa.apiservice.application.dto.auth.idempotency;

import java.util.regex.Pattern;

public record RegistrationRequestHash(String value) {
  private static final Pattern SHA_256_HEX = Pattern.compile("^[a-f0-9]{64}$");

  public RegistrationRequestHash {
    if (value == null || value.isBlank() || !SHA_256_HEX.matcher(value).matches()) {
      throw new IllegalArgumentException("Invalid registration request hash");
    }
  }

  @Override
  public String toString() {
    return "RegistrationRequestHash[redacted]";
  }
}
