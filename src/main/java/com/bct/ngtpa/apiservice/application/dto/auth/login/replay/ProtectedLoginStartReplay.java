package com.bct.ngtpa.apiservice.application.dto.auth.login.replay;

import java.util.Objects;

public record ProtectedLoginStartReplay(
    String keyVersion, String algorithm, String iv, String ciphertext) {
  public ProtectedLoginStartReplay {
    Objects.requireNonNull(keyVersion);
    Objects.requireNonNull(algorithm);
    Objects.requireNonNull(iv);
    Objects.requireNonNull(ciphertext);
  }

  public ProtectedLoginStartReplay withKeyVersion(String value) {
    return new ProtectedLoginStartReplay(value, algorithm, iv, ciphertext);
  }

  public ProtectedLoginStartReplay withCiphertext(String value) {
    return new ProtectedLoginStartReplay(keyVersion, algorithm, iv, value);
  }

  @Override
  public String toString() {
    return "ProtectedLoginStartReplay[keyVersion="
        + keyVersion
        + ", algorithm="
        + algorithm
        + ", iv=<redacted>, ciphertext=<redacted>]";
  }
}
