package com.bct.ngtpa.apiservice.adapter.out.captcha;

import com.bct.ngtpa.apiservice.application.dto.auth.CaptchaPolicy;
import java.security.SecureRandom;
import java.util.Objects;

public final class SecureRandomCaptchaTextGenerator {

  private final SecureRandom secureRandom;

  public SecureRandomCaptchaTextGenerator() {
    this(new SecureRandom());
  }

  public SecureRandomCaptchaTextGenerator(SecureRandom secureRandom) {
    this.secureRandom = Objects.requireNonNull(secureRandom, "secureRandom must not be null");
  }

  public String generate(CaptchaPolicy policy) {
    Objects.requireNonNull(policy, "policy must not be null");
    if (policy.length() <= 0) {
      throw new IllegalArgumentException("CaptchaPolicy length is invalid");
    }
    if (policy.alphabet() == null || policy.alphabet().isBlank()) {
      throw new IllegalArgumentException("CaptchaPolicy alphabet is invalid");
    }

    StringBuilder value = new StringBuilder(policy.length());
    for (int index = 0; index < policy.length(); index++) {
      value.append(policy.alphabet().charAt(secureRandom.nextInt(policy.alphabet().length())));
    }
    return value.toString();
  }
}
