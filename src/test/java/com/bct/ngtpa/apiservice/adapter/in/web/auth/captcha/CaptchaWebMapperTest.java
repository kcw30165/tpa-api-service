package com.bct.ngtpa.apiservice.adapter.in.web.auth.captcha;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.adapter.in.web.auth.captcha.mapper.CaptchaWebMapper;
import com.bct.ngtpa.apiservice.adapter.in.web.response.ApiStatus;
import com.bct.ngtpa.apiservice.adapter.in.web.response.MutationResponse;
import com.bct.ngtpa.apiservice.application.dto.auth.captcha.CaptchaChallengeId;
import com.bct.ngtpa.apiservice.application.dto.auth.captcha.CaptchaImage;
import com.bct.ngtpa.apiservice.application.dto.auth.captcha.CaptchaVerificationOutcome;
import com.bct.ngtpa.apiservice.application.dto.auth.captcha.CreateCaptchaChallengeResult;
import com.bct.ngtpa.apiservice.application.dto.auth.captcha.RefreshCaptchaChallengeResult;
import com.bct.ngtpa.apiservice.application.dto.auth.captcha.VerifyCaptchaChallengeResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class CaptchaWebMapperTest {

  private static final CaptchaChallengeId CHALLENGE_ID =
      new CaptchaChallengeId("cap_abcdefghijklmnop");
  private static final CaptchaChallengeId NEW_CHALLENGE_ID =
      new CaptchaChallengeId("cap_bcdefghijklmnopq");
  private final CaptchaWebMapper mapper =
      new CaptchaWebMapper(
          (code, locale, accountEnv, trustCode, schemeType) -> "resolved-" + code + "-" + locale);

  @Test
  void captchaChallengeMapsUseCaseResultToMutationResponse() {
    MutationResponse<?> response =
        mapper.toChallengeMutationResponse(
            new CreateCaptchaChallengeResult(
                CHALLENGE_ID, new CaptchaImage("base64-image", "image/png"), 300));

    assertThat(response.success()).isTrue();
    assertThat(response.status()).isEqualTo(ApiStatus.SUCCESS);
    assertThat(response.result().toString()).contains("nextStep=NONE", "data=");
    assertThat(response.result().toString()).doesNotContain(CHALLENGE_ID.value(), "base64-image");
  }

  @Test
  void captchaVerifyMapsInvalidAnswerToValidationFailure() {
    MutationResponse<?> response =
        mapper.toVerifyMutationResponse(
            new VerifyCaptchaChallengeResult(CaptchaVerificationOutcome.INVALID, false, null, 4),
            "zh-HK");

    assertThat(response.success()).isFalse();
    assertThat(response.status()).isEqualTo(ApiStatus.VALIDATION_FAILED);
    assertThat(response.result().toString()).contains("nextStep=NONE", "data=");
    assertThat(response.errors())
        .singleElement()
        .satisfies(
            error -> {
              assertThat(error.code()).isEqualTo("err.captcha.verification.invalid");
              assertThat(error.targets()).containsExactly("captchaAnswer");
            });
  }

  @Test
  void captchaVerifyMapsLifecycleOutcomesToValidationFailure() {
    assertThat(
            mapper
                .toVerifyMutationResponse(
                    new VerifyCaptchaChallengeResult(
                        CaptchaVerificationOutcome.EXPIRED, false, null, 0),
                    "en")
                .errors()
                .getFirst()
                .code())
        .isEqualTo("err.captcha.verification.invalid");
    assertThat(
            mapper
                .toVerifyMutationResponse(
                    new VerifyCaptchaChallengeResult(
                        CaptchaVerificationOutcome.LOCKED, false, null, 0),
                    "en")
                .errors()
                .getFirst()
                .code())
        .isEqualTo("err.captcha.verification.invalid");
    assertThat(
            mapper
                .toVerifyMutationResponse(
                    new VerifyCaptchaChallengeResult(
                        CaptchaVerificationOutcome.CONSUMED, false, null, 0),
                    "en")
                .errors()
                .getFirst()
                .code())
        .isEqualTo("err.captcha.verification.invalid");
    assertThat(
            mapper
                .toVerifyMutationResponse(
                    new VerifyCaptchaChallengeResult(
                        CaptchaVerificationOutcome.NOT_FOUND, false, null, 0),
                    "en")
                .errors()
                .getFirst()
                .code())
        .isEqualTo("err.captcha.verification.invalid");
    assertThat(
            mapper
                .toVerifyMutationResponse(
                    new VerifyCaptchaChallengeResult(
                        CaptchaVerificationOutcome.REFRESHED, false, null, 0),
                    "en")
                .errors()
                .getFirst()
                .code())
        .isEqualTo("err.captcha.verification.invalid");
  }

  @Test
  void captchaVerifyMapsIneligibleStateToApprovedPrivateFailureContract() throws Exception {
    MutationResponse<?> response =
        mapper.toVerifyMutationResponse(
            new VerifyCaptchaChallengeResult(CaptchaVerificationOutcome.INVALID, false, null, 0),
            "en-HK");
    String json = new ObjectMapper().writeValueAsString(response);

    assertThat(response.success()).isFalse();
    assertThat(response.status()).isEqualTo(ApiStatus.VALIDATION_FAILED);
    assertThat(response.messages()).isEmpty();
    assertThat(response.errors())
        .singleElement()
        .satisfies(
            error -> {
              assertThat(error.code()).isEqualTo("err.captcha.verification.invalid");
              assertThat(error.targets()).containsExactly("captchaAnswer");
            });
    assertThat(json)
        .contains("\"nextStep\":\"NONE\"")
        .doesNotContain("VERIFIED", "answerHash", "Redis", "Exception", "wrong", "aBc123");
  }

  @Test
  void captchaRefreshMapsUseCaseResultToMutationResponse() {
    MutationResponse<?> response =
        mapper.toRefreshMutationResponse(
            new RefreshCaptchaChallengeResult(
                NEW_CHALLENGE_ID, new CaptchaImage("base64-refresh", "image/png"), 300));

    assertThat(response.success()).isTrue();
    assertThat(response.status()).isEqualTo(ApiStatus.SUCCESS);
    assertThat(response.result().toString()).contains("nextStep=NONE", "data=");
    assertThat(response.result().toString())
        .doesNotContain(NEW_CHALLENGE_ID.value(), "base64-refresh");
  }

  @Test
  void captchaWebMapperUsesMutationResponseNotWorkflowResponse() throws Exception {
    MutationResponse<?> response =
        mapper.toChallengeMutationResponse(
            new CreateCaptchaChallengeResult(
                CHALLENGE_ID, new CaptchaImage("base64-image", "image/png"), 300));
    String json = new ObjectMapper().writeValueAsString(response);
    String source =
        Files.readString(
            Path.of(
                "src/main/java/com/bct/ngtpa/apiservice/adapter/in/web/auth/captcha/mapper/CaptchaWebMapper.java"));

    assertThat(response).isInstanceOf(MutationResponse.class);
    assertThat(json).contains("\"nextStep\":\"NONE\"", "\"data\":");
    assertThat(json).doesNotContain("pageRef");
    assertThat(source)
        .doesNotContain("WorkflowMutationResponseMapper", "WorkflowResultPayload", "pageRef");
  }

  @Test
  void captchaWebMapperDoesNotHardcodeUserFacingMessages() throws Exception {
    String mapperSource =
        Files.readString(
            Path.of(
                "src/main/java/com/bct/ngtpa/apiservice/adapter/in/web/auth/captcha/mapper/CaptchaWebMapper.java"));
    String controllerSource =
        Files.readString(
            Path.of(
                "src/main/java/com/bct/ngtpa/apiservice/adapter/in/web/auth/captcha/CaptchaController.java"));

    assertThat(mapperSource + controllerSource)
        .doesNotContain("Invalid captcha")
        .doesNotContain("Captcha expired")
        .doesNotContain("Too many attempts")
        .doesNotContain("驗證碼");
  }
}
