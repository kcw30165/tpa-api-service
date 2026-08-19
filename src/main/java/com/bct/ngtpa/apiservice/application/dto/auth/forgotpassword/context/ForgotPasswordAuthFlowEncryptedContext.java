package com.bct.ngtpa.apiservice.application.dto.auth.forgotpassword.context;

public record ForgotPasswordAuthFlowEncryptedContext(
    String ciphertext, String keyVersion, String algorithm) {
  public static final String DEFAULT_ALGORITHM = "AES_GCM_V1";

  public ForgotPasswordAuthFlowEncryptedContext {
    ciphertext = requireText(ciphertext, "ciphertext");
    keyVersion = requireText(keyVersion, "keyVersion");
    algorithm = requireText(algorithm, "algorithm");
  }

  public ForgotPasswordAuthFlowEncryptedContext withCiphertext(String value) {
    return new ForgotPasswordAuthFlowEncryptedContext(value, keyVersion, algorithm);
  }

  private static String requireText(String value, String field) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(field + " must not be blank");
    }
    return value.trim();
  }

  @Override
  public String toString() {
    return "ForgotPasswordAuthFlowEncryptedContext[keyVersion="
        + keyVersion
        + ", algorithm="
        + algorithm
        + ", ciphertext=<redacted>]";
  }
}
