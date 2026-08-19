package com.bct.ngtpa.apiservice.application.usecase.auth.login.otp.resend;

import com.bct.ngtpa.apiservice.application.dto.auth.login.idempotency.LoginOtpSendRequestHash;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.HexFormat;

public final class LoginOtpResendRequestHashGenerator {
  public LoginOtpSendRequestHash generate(ResendLoginOtpCommand c) {
    try {
      return new LoginOtpSendRequestHash(
          HexFormat.of()
              .formatHex(
                  MessageDigest.getInstance("SHA-256")
                      .digest(
                          ("LOGIN_OTP_RESEND\u0000" + c.authFlowSessionId())
                              .getBytes(StandardCharsets.UTF_8))));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }
}
