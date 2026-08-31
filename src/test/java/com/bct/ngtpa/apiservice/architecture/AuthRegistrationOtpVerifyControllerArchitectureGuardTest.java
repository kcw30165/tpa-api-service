package com.bct.ngtpa.apiservice.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class AuthRegistrationOtpVerifyControllerArchitectureGuardTest {
  private static final Path CONTROLLER =
      Path.of(
          "src/main/java/com/bct/ngtpa/apiservice/adapter/in/web/auth/registration/RegistrationController.java");

  @Test
  void otpVerifyControllerDoesNotDependOnOutboundAdapters() throws Exception {
    String verifyOtp = methodSource(Files.readString(CONTROLLER), "verifyOtp(");

    assertThat(verifyOtp)
        .doesNotContain(
            "adapter.out.redis",
            "adapter.out.apim",
            "adapter.out.sms",
            "adapter.out.email",
            "ApimWebClientFacade",
            "ReactiveStringRedisTemplate",
            "OtpChallengePort",
            "MessageDeliveryPort",
            "RegistrationLookupPort");
  }

  @Test
  void p7_5DoesNotIntroduceUsernameCompleteCancelOrResume() throws Exception {
    String verifyOtp = methodSource(Files.readString(CONTROLLER), "verifyOtp(");

    assertThat(verifyOtp)
        .doesNotContain(
            "/api/v1/auth/registration/username/check",
            "/api/v1/auth/registration/complete",
            "/api/v1/auth/registration/cancel",
            "/api/v1/auth/flow/resume",
            "AuthFlowResumeRequest",
            "ResumeAuthFlowUseCase");
  }

  private static String methodSource(String source, String methodMarker) {
    int marker = source.indexOf(methodMarker);
    assertThat(marker).as("method marker %s", methodMarker).isGreaterThanOrEqualTo(0);
    int mapping = source.lastIndexOf("@PostMapping", marker);
    int openingBrace = source.indexOf('{', marker);
    assertThat(mapping).isGreaterThanOrEqualTo(0);
    assertThat(openingBrace).isGreaterThan(marker);
    int depth = 0;
    for (int index = openingBrace; index < source.length(); index++) {
      char current = source.charAt(index);
      if (current == '{') {
        depth++;
      } else if (current == '}') {
        depth--;
        if (depth == 0) {
          return source.substring(mapping, index + 1);
        }
      }
    }
    throw new AssertionError("Unbalanced method body for " + methodMarker);
  }
}
