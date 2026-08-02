package com.bct.ngtpa.apiservice.architecture;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ForgotPasswordOtpVerifyIdempotencyHashArchitectureGuardTest {

    @Test
    void forgotPasswordOtpVerifyHashExcludesOtpAndSessionIdentity() throws Exception {
        Path source = Path.of(System.getProperty("user.dir"))
                .resolve(
                        "src/main/java/com/bct/ngtpa/apiservice/application/usecase/auth/forgotpassword/otp/verify/VerifyForgotPasswordOtpService.java")
                .normalize();
        String service = Files.readString(source);

        assertTrue(service.contains("ForgotPasswordIdempotencyAction.OTP_VERIFY"));
        assertFalse(service.contains("session.authFlowSessionId().value() + \"|\" + command.code()"));
        assertTrue(service.contains("sha256(\"FORGOT_PASSWORD\\u0000OTP_VERIFY\\u0000v1\")"));
    }
}