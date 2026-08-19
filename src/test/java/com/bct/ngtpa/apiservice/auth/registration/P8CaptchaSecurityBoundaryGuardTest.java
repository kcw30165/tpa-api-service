package com.bct.ngtpa.apiservice.auth.registration;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class P8CaptchaSecurityBoundaryGuardTest {

  @Test
  void captchaEndpointsRemainUnauthenticatedPreStart() throws Exception {
    String source =
        Files.readString(
            Path.of(
                "src/main/java/com/bct/ngtpa/apiservice/adapter/in/web/auth/captcha/CaptchaController.java"));

    assertThat(source)
        .doesNotContain("auth_flow_sid")
        .doesNotContain("X-XSRF-TOKEN")
        .doesNotContain("Idempotency-Key")
        .doesNotContain("Account-Ref");
  }

  @Test
  void captchaEndpointsStillUseMutationResponseWithResultWrapperOnly() throws Exception {
    String controller =
        Files.readString(
            Path.of(
                "src/main/java/com/bct/ngtpa/apiservice/adapter/in/web/auth/captcha/CaptchaController.java"));
    String mapper =
        Files.readString(
            Path.of(
                "src/main/java/com/bct/ngtpa/apiservice/adapter/in/web/auth/captcha/mapper/CaptchaWebMapper.java"));

    assertThat(controller + mapper)
        .contains("MutationResponse")
        .contains("CaptchaWorkflowResultResponse")
        .contains("WorkflowNextStep.NONE")
        .doesNotContain("WorkflowResultPayload")
        .doesNotContain("pageRef");
  }

  @Test
  void p8RegistrationStartGateDoesNotChangeP7OtpEndpoints() throws Exception {
    String resend =
        Files.readString(
            Path.of(
                "src/main/java/com/bct/ngtpa/apiservice/adapter/in/web/controller/AuthRegistrationOtpController.java"));
    String verify =
        Files.readString(
            Path.of(
                "src/main/java/com/bct/ngtpa/apiservice/adapter/in/web/controller/AuthRegistrationOtpController.java"));

    assertThat(resend)
        .contains("auth_flow_sid")
        .contains("X-XSRF-TOKEN")
        .contains("Idempotency-Key")
        .doesNotContain("captchaChallengeId")
        .doesNotContain("AuthCaptchaController");
    assertThat(verify)
        .contains("auth_flow_sid")
        .contains("X-XSRF-TOKEN")
        .contains("Idempotency-Key")
        .doesNotContain("captchaChallengeId")
        .doesNotContain("AuthCaptchaController");
  }

  @Test
  void registrationStartCaptchaFailureProductionPathDoesNotExposeSensitiveValues()
      throws Exception {
    String combined =
        Files.readString(
                Path.of(
                    "src/main/java/com/bct/ngtpa/apiservice/application/usecase/auth/registration/StartRegistrationService.java"))
            + Files.readString(
                Path.of(
                    "src/main/java/com/bct/ngtpa/apiservice/adapter/in/web/auth/registration/mapper/RegistrationStartWebMapper.java"));

    assertThat(combined)
        .doesNotContain("answerHash")
        .doesNotContain("imageBase64")
        .doesNotContain("ngtpa:auth:captcha:")
        .doesNotContain("captchaAnswer")
        .doesNotContain("auth_flow_sid value");
  }
}
