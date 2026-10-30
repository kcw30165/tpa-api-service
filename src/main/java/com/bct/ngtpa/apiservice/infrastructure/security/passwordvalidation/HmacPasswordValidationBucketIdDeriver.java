package com.bct.ngtpa.apiservice.infrastructure.security.passwordvalidation;

import com.bct.ngtpa.apiservice.application.port.out.PasswordValidationBucketIdPort;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public final class HmacPasswordValidationBucketIdDeriver implements PasswordValidationBucketIdPort {
  private final byte[] secret;

  public HmacPasswordValidationBucketIdDeriver(byte[] secret) {
    this.secret = secret == null ? new byte[0] : secret.clone();
  }

  @Override
  public String derive(String normalizedUsername) {
    if (secret.length < 32 || normalizedUsername == null || normalizedUsername.isBlank()) {
      throw new IllegalStateException("Password-validation limiter identity unavailable");
    }
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(secret, "HmacSHA256"));
      return HexFormat.of()
          .formatHex(
              mac.doFinal(
                  ("ngtpa:password-validation:username:v1:" + normalizedUsername)
                      .getBytes(StandardCharsets.UTF_8)));
    } catch (java.security.GeneralSecurityException exception) {
      throw new IllegalStateException("Password-validation limiter identity unavailable");
    }
  }
}
