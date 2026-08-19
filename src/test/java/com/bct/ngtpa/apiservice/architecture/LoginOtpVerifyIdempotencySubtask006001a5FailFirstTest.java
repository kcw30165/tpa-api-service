package com.bct.ngtpa.apiservice.architecture;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.*;
import org.junit.jupiter.api.Test;

class LoginOtpVerifyIdempotencySubtask006001a5FailFirstTest {
  private static Path projectPath(String path) {
    return Path.of(System.getProperty("user.dir")).resolve(path).normalize();
  }

  private static String source(String path) throws Exception {
    return Files.readString(projectPath(path));
  }

  @Test
  void approvedVocabularyAndBoundaryExist() throws Exception {
    assertTrue(
        source(
                "src/main/java/com/bct/ngtpa/apiservice/application/dto/auth/login/idempotency/LoginIdempotencyAction.java")
            .contains("OTP_VERIFY"));
    assertTrue(
        Files.exists(
            projectPath(
                "src/main/java/com/bct/ngtpa/apiservice/application/port/out/LoginOtpVerifyPersistencePort.java")));
    assertTrue(
        Files.exists(
            projectPath(
                "src/main/java/com/bct/ngtpa/apiservice/application/dto/auth/login/otp/verify/LoginOtpConsumptionProof.java")));
    String service =
        source(
            "src/main/java/com/bct/ngtpa/apiservice/application/usecase/auth/login/otp/verify/LoginOtpVerifyService.java");
    assertTrue(service.contains("LoginOtpVerifyPersistencePort"));
    assertTrue(service.contains("AuthFlowSessionMutationLockPort"));
    assertFalse(service.contains(".verifyChallenge(new VerifyOtpChallengeCommand"));
    assertFalse(service.contains("AuthFlowSessionCompletionLockPort"));
  }

  @Test
  void canonicalHashDoesNotDependOnOtpOrTransportState() throws Exception {
    String hash =
        source(
            "src/main/java/com/bct/ngtpa/apiservice/application/service/auth/login/otp/verify/LoginOtpVerifyRequestHasher.java");
    assertTrue(hash.contains("LOGIN\\u0000OTP_VERIFY\\u0000v1"));
    for (String prohibited :
        new String[] {
          "otpCode",
          "authFlowSessionId",
          "csrf",
          "challengeId",
          "requestId",
          "acceptLanguage",
          "accountRef"
        }) assertFalse(hash.contains(prohibited));
  }
}
