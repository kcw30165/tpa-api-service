package com.bct.ngtpa.apiservice.application.usecase.auth.login.otp;

import com.bct.ngtpa.apiservice.application.dto.auth.login.otp.SendLoginOtpCommand;
import com.bct.ngtpa.apiservice.application.dto.auth.login.otp.idempotency.LoginOtpSendRequestHash;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.HexFormat;

public final class LoginOtpSendRequestHashGenerator {
    public LoginOtpSendRequestHash generate(SendLoginOtpCommand c) {
        String canonical = "LOGIN_OTP_SEND\u0000" + c.authFlowSessionId() + "\u0000" + c.destinationId();
        try {
            return new LoginOtpSendRequestHash(HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8))));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
