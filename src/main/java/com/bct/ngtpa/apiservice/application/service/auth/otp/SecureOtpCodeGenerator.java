package com.bct.ngtpa.apiservice.application.service.auth.otp;

import java.security.SecureRandom;

public final class SecureOtpCodeGenerator implements OtpCodeGenerator {

  private static final char[] NUMERIC_CHARACTERS = "0123456789".toCharArray();
  private static final char[] ALPHANUMERIC_CHARACTERS =
      "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789".toCharArray();

  private final SecureRandom secureRandom;

  public SecureOtpCodeGenerator() {
    this(new SecureRandom());
  }

  SecureOtpCodeGenerator(SecureRandom secureRandom) {
    this.secureRandom = secureRandom;
  }

  @Override
  public String generate(int length, boolean numericOnly) {
    if (length <= 0) {
      throw new IllegalArgumentException("OTP length must be greater than zero.");
    }

    char[] alphabet = numericOnly ? NUMERIC_CHARACTERS : ALPHANUMERIC_CHARACTERS;
    StringBuilder value = new StringBuilder(length);
    for (int index = 0; index < length; index++) {
      value.append(alphabet[secureRandom.nextInt(alphabet.length)]);
    }
    return value.toString();
  }
}
