package com.bct.ngtpa.apiservice.application.dto.auth.usernamerecovery.context;

public record UsernameRecoveryAuthFlowEncryptedContext(
    String ciphertext, String keyVersion, String algorithm) {
  public static final String DEFAULT_ALGORITHM = "AES/GCM/NoPadding";

  public UsernameRecoveryAuthFlowEncryptedContext {
    ciphertext = require(ciphertext, "ciphertext");
    keyVersion = require(keyVersion, "keyVersion");
    algorithm = require(algorithm, "algorithm");
  }

  private static String require(String value, String name) {
    if (value == null || value.isBlank())
      throw new IllegalArgumentException(name + " must not be blank");
    return value.trim();
  }

  public UsernameRecoveryAuthFlowEncryptedContext withCiphertext(String value) {
    return new UsernameRecoveryAuthFlowEncryptedContext(value, keyVersion, algorithm);
  }

  @Override
  public String toString() {
    return "UsernameRecoveryAuthFlowEncryptedContext[keyVersion="
        + keyVersion
        + ", algorithm="
        + algorithm
        + ", ciphertext=<redacted>]";
  }
}
