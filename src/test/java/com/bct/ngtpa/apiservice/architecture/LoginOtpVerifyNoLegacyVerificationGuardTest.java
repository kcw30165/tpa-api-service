package com.bct.ngtpa.apiservice.architecture;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class LoginOtpVerifyNoLegacyVerificationGuardTest {

    @Test
    void loginVerifyUsesDedicatedBoundaryAndPreservesFirstRequestContinuation() throws Exception {
        Path source = Path.of(System.getProperty("user.dir"))
                .resolve(
                        "src/main/java/com/bct/ngtpa/apiservice/application/usecase/auth/login/otp/verify/LoginOtpVerifyService.java")
                .normalize();
        String service = Files.readString(source);

        assertTrue(service.contains("LoginOtpVerifyPersistencePort"));
        assertFalse(service.contains(".verifyChallenge(new VerifyOtpChallengeCommand"));
        assertTrue(service.contains("case VERIFIED_AND_CONSUMED ->"));
        assertTrue(service.contains(".markVerified(c.authFlowSessionId(), c.correlationId())"));
        assertTrue(service.contains("continueToAccounts(c, x)"));
        assertTrue(service.contains("case ALREADY_COMMITTED ->"));
        assertTrue(service.contains("VerifyLoginOtpStatus.SYSTEM_FAILED"));
        assertFalse(service.contains("AuthFlowSessionCompletionLockPort"));
    }
}
