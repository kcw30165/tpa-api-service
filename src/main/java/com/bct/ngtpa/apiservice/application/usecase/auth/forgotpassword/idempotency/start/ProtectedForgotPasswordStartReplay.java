package com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.idempotency.start;

/** AES-GCM protected Forgot Password Start replay wire value. */
public record ProtectedForgotPasswordStartReplay(
    int schemaVersion, String keyVersion, String algorithm, String iv, String ciphertext) {
  public ProtectedForgotPasswordStartReplay {
    if (schemaVersion != 1)
      throw new IllegalArgumentException("unsupported protected replay schema");
    keyVersion = require(keyVersion, "keyVersion");
    algorithm = require(algorithm, "algorithm");
    iv = require(iv, "iv");
    ciphertext = require(ciphertext, "ciphertext");
  }

  @Override
  public String toString() {
    return "ProtectedForgotPasswordStartReplay[schemaVersion="
        + schemaVersion
        + ", keyVersion="
        + keyVersion
        + ", algorithm="
        + algorithm
        + ", iv=<redacted>, ciphertext=<redacted>]";
  }

  private static String require(String value, String name) {
    if (value == null || value.isBlank())
      throw new IllegalArgumentException(name + " must not be blank");
    return value.trim();
  }
}
