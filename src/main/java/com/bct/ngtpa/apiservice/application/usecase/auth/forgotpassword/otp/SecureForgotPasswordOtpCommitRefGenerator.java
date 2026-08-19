package com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.otp;

import java.security.SecureRandom;
import java.util.Base64;

public final class SecureForgotPasswordOtpCommitRefGenerator {
  private final SecureRandom random = new SecureRandom();

  public String generate() {
    byte[] b = new byte[16];
    random.nextBytes(b);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
  }

  @Override
  public String toString() {
    return "SecureForgotPasswordOtpCommitRefGenerator[secureRandom=<redacted>]";
  }
}
