package com.bct.ngtpa.apiservice.auth.registration;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.adapter.in.web.auth.registration.dto.RegistrationStartRequest;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.registration.mapper.RegistrationStartWebMapper;
import com.bct.ngtpa.apiservice.application.dto.auth.idempotency.RegistrationIdempotencyStartOutcome;
import com.bct.ngtpa.apiservice.application.dto.workflow.RegistrationErrorKey;
import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowNextStep;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.StartRegistrationError;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.StartRegistrationResult;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.StartRegistrationResultStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.reflect.RecordComponent;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class P8CaptchaIntegrationTest {

  private final RegistrationStartWebMapper mapper =
      new RegistrationStartWebMapper(Duration.ofMinutes(5));
  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  void registrationStartRequestShapeKeepsCaptchaChallengeIdOnly() {
    assertThat(
            Arrays.stream(RegistrationStartRequest.class.getRecordComponents())
                .map(RecordComponent::getName))
        .containsExactly(
            "identityType", "identityValue", "dateOfBirth", "email", "mobile", "captchaChallengeId")
        .doesNotContain(
            "captchaAnswer", "captchaVerified", "imageBase64", "answerHash", "lang", "accountRef");
  }

  @Test
  void registrationStartSuccessDoesNotReturnCaptchaData() throws Exception {
    String json =
        objectMapper.writeValueAsString(
            mapper.toMutationResponse(
                new StartRegistrationResult(
                    StartRegistrationResultStatus.STARTED,
                    WorkflowNextStep.VERIFY_OTP,
                    "m****r@example.com",
                    300,
                    60,
                    5,
                    "csrf_token_safe",
                    "afs_secret",
                    List.of(),
                    List.of(),
                    RegistrationIdempotencyStartOutcome.STARTED,
                    null),
                "en"));

    assertThat(json)
        .contains("VERIFY_OTP")
        .doesNotContain("captchaChallengeId")
        .doesNotContain("captchaStatus")
        .doesNotContain("answerHash")
        .doesNotContain("imageBase64")
        .doesNotContain("ngtpa:auth:captcha:");
  }

  @Test
  void registrationStartCaptchaFailureDoesNotExposeSensitiveValues() throws Exception {
    String json =
        objectMapper.writeValueAsString(
            mapper.toMutationResponse(
                new StartRegistrationResult(
                    StartRegistrationResultStatus.VALIDATION_FAILED,
                    WorkflowNextStep.NONE,
                    null,
                    0,
                    0,
                    0,
                    null,
                    null,
                    List.of(),
                    List.of(
                        StartRegistrationError.withoutMessage(
                            RegistrationErrorKey.REGISTRATION_CAPTCHA_INVALID,
                            "captchaChallengeId")),
                    RegistrationIdempotencyStartOutcome.STARTED,
                    null),
                "en"));

    assertThat(json)
        .contains("registration.captcha.invalid")
        .contains("captchaChallengeId")
        .doesNotContain("cap_")
        .doesNotContain("answerHash")
        .doesNotContain("imageBase64")
        .doesNotContain("identityValue")
        .doesNotContain("member@example.com")
        .doesNotContain("31/01/1980");
  }
}
