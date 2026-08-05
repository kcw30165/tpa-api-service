package com.bct.ngtpa.apiservice.application.service.auth.login.otp.verify;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

public final class LoginOtpVerifyRequestHasher {
    private static final String CANONICAL = "LOGIN\u0000OTP_VERIFY\u0000v1";

    public String hash() {
        try {
            return HexFormat.of()
                    .formatHex(MessageDigest.getInstance("SHA-256").digest(CANONICAL.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("request hash unavailable");
        }
    }
}
