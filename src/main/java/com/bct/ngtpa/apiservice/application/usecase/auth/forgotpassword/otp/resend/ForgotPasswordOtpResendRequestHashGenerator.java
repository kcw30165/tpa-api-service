package com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.otp.resend;

import com.bct.ngtpa.apiservice.application.dto.auth.forgotpassword.idempotency.ForgotPasswordRequestHash;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

public final class ForgotPasswordOtpResendRequestHashGenerator {
    public ForgotPasswordRequestHash generate() {
        try {
            return new ForgotPasswordRequestHash(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest("forgot-password:otp-resend:v1".getBytes(StandardCharsets.UTF_8))));
        } catch (Exception e) {
            throw new IllegalStateException("resend request hash unavailable");
        }
    }
}
