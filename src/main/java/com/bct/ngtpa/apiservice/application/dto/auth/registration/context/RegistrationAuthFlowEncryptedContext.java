package com.bct.ngtpa.apiservice.application.dto.auth.registration.context;

public record RegistrationAuthFlowEncryptedContext(
    String ciphertext, String keyVersion, String algorithm) {
  public static final String DEFAULT_ALGORITHM = "AES_GCM_V1";

  public RegistrationAuthFlowEncryptedContext {
    ciphertext = requireText(ciphertext, "ciphertext");
    keyVersion = requireText(keyVersion, "keyVersion");
    algorithm = algorithm == null || algorithm.isBlank() ? DEFAULT_ALGORITHM : algorithm.trim();
  }

  public RegistrationAuthFlowEncryptedContext withCiphertext(String value) {
    return new RegistrationAuthFlowEncryptedContext(value, keyVersion, algorithm);
  }

  private static String requireText(String value, String field) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(field + " must not be blank");
    }
    return value.trim();
  }

  @Override
  public String toString() {
    return "RegistrationAuthFlowEncryptedContext[ciphertext=<redacted>, keyVersion="
        + keyVersion
        + ", algorithm="
        + algorithm
        + "]";
  }
}
