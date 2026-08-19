package com.bct.ngtpa.apiservice.architecture;

import static com.bct.ngtpa.apiservice.architecture.SourceTextAssertions.*;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class LoginOtpVerifyNoLegacyVerificationGuardTest {

  @Test
  void loginVerifyUsesDedicatedBoundaryAndPreservesFirstRequestContinuation() throws Exception {
    Path source =
        Path.of(System.getProperty("user.dir"))
            .resolve(
                "src/main/java/com/bct/ngtpa/apiservice/application/usecase/auth/login/otp/verify/LoginOtpVerifyService.java")
            .normalize();
    String service = Files.readString(source);

    assertTrue(containsIgnoringWhitespace(service, "LoginOtpVerifyPersistencePort"));
    assertFalse(
        containsIgnoringWhitespace(service, ".verifyChallenge(new VerifyOtpChallengeCommand"));
    assertTrue(containsIgnoringWhitespace(service, "case VERIFIED_AND_CONSUMED ->"));
    assertTrue(
        containsIgnoringWhitespace(
            service, ".markVerified(c.authFlowSessionId(), c.correlationId())"));
    assertTrue(containsIgnoringWhitespace(service, "continueToAccounts(c, x)"));
    assertTrue(containsIgnoringWhitespace(service, "case ALREADY_COMMITTED ->"));
    assertTrue(containsIgnoringWhitespace(service, "VerifyLoginOtpStatus.SYSTEM_FAILED"));
    assertFalse(containsIgnoringWhitespace(service, "AuthFlowSessionCompletionLockPort"));
  }
}
