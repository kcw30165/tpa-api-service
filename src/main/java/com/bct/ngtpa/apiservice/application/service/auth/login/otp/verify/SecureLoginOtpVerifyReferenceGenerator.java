package com.bct.ngtpa.apiservice.application.service.auth.login.otp.verify;

import com.bct.ngtpa.apiservice.application.dto.auth.login.otp.verify.*;
import java.security.SecureRandom;
import java.util.HexFormat;

public final class SecureLoginOtpVerifyReferenceGenerator {
    private final SecureRandom random = new SecureRandom();

    private String next() {
        byte[] b = new byte[16];
        random.nextBytes(b);
        return HexFormat.of().formatHex(b);
    }

    public AuthenticatedLoginHandoffReference handoff() {
        return new AuthenticatedLoginHandoffReference(next());
    }

    public LoginOtpVerificationOperationReference operation() {
        return new LoginOtpVerificationOperationReference(next());
    }
}
