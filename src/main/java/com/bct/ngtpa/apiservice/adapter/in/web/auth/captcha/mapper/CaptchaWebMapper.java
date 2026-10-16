package com.bct.ngtpa.apiservice.adapter.in.web.auth.captcha.mapper;

import com.bct.ngtpa.apiservice.adapter.in.web.auth.captcha.dto.CaptchaChallengeResponse;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.captcha.dto.CaptchaRefreshResponse;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.captcha.dto.CaptchaVerifyResponse;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.captcha.dto.CaptchaWorkflowResultResponse;
import com.bct.ngtpa.apiservice.adapter.in.web.response.ApiError;
import com.bct.ngtpa.apiservice.adapter.in.web.response.ApiStatus;
import com.bct.ngtpa.apiservice.adapter.in.web.response.MutationResponse;
import com.bct.ngtpa.apiservice.application.dto.auth.captcha.CreateCaptchaChallengeResult;
import com.bct.ngtpa.apiservice.application.dto.auth.captcha.RefreshCaptchaChallengeResult;
import com.bct.ngtpa.apiservice.application.dto.auth.captcha.VerifyCaptchaChallengeResult;
import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowNextStep;
import com.bct.ngtpa.apiservice.shared.error.ErrorCodes;
import java.util.List;
import java.util.Objects;

public class CaptchaWebMapper {
  private static final String RESULT_NOT_NULL = "result must not be null";

  /**
   * Temporary source-compatibility target for existing tests that supplied a message-resolver
   * lambda. The mapper never invokes this function and no localized text is returned. Remove with
   * the legacy test constructors in the final regression/evidence subtask.
   */
  @FunctionalInterface
  public interface IgnoredMessageResolver {
    String resolve(
        String code, String language, String accountEnv, String trustCode, String schemeType);
  }

  private static final String SOURCE_SERVER = "SERVER";
  private static final String CAPTCHA_ANSWER_TARGET = "captchaAnswer";
  private static final String CAPTCHA_CHALLENGE_ID_TARGET = "captchaChallengeId";

  /**
   * Kept temporarily for constructor compatibility during the staged 0042 migration. Captcha public
   * responses intentionally do not resolve Java-owned display text.
   */
  public CaptchaWebMapper() {}

  public CaptchaWebMapper(IgnoredMessageResolver ignoredMessageResolver) {
    java.util.Objects.requireNonNull(
        ignoredMessageResolver, "ignoredMessageResolver must not be null");
  }

  public MutationResponse<CaptchaWorkflowResultResponse<CaptchaChallengeResponse>>
      toChallengeMutationResponse(CreateCaptchaChallengeResult result) {
    return MutationResponse.success(
        ApiStatus.SUCCESS, wrap(toChallengeResponse(result)), List.of());
  }

  public MutationResponse<CaptchaWorkflowResultResponse<CaptchaVerifyResponse>>
      toVerifyMutationResponse(VerifyCaptchaChallengeResult result, String acceptLanguage) {
    Objects.requireNonNull(result, RESULT_NOT_NULL);
    return switch (result.outcome()) {
      case VERIFIED ->
          MutationResponse.success(ApiStatus.SUCCESS, wrap(toVerifyResponse(result)), List.of());
      case INVALID ->
          failure(result, ErrorCodes.CAPTCHA_VERIFICATION_INVALID, CAPTCHA_ANSWER_TARGET);
      case EXPIRED ->
          failure(result, ErrorCodes.CAPTCHA_VERIFICATION_INVALID, CAPTCHA_ANSWER_TARGET);
      case LOCKED ->
          failure(result, ErrorCodes.CAPTCHA_VERIFICATION_INVALID, CAPTCHA_ANSWER_TARGET);
      case CONSUMED ->
          failure(result, ErrorCodes.CAPTCHA_VERIFICATION_INVALID, CAPTCHA_ANSWER_TARGET);
      case REFRESHED ->
          failure(result, ErrorCodes.CAPTCHA_VERIFICATION_INVALID, CAPTCHA_ANSWER_TARGET);
      case NOT_FOUND ->
          failure(result, ErrorCodes.CAPTCHA_VERIFICATION_INVALID, CAPTCHA_ANSWER_TARGET);
    };
  }

  public MutationResponse<CaptchaWorkflowResultResponse<CaptchaRefreshResponse>>
      toRefreshMutationResponse(RefreshCaptchaChallengeResult result) {
    return MutationResponse.success(ApiStatus.SUCCESS, wrap(toRefreshResponse(result)), List.of());
  }

  public MutationResponse<CaptchaWorkflowResultResponse<CaptchaVerifyResponse>>
      missingAnswerResponse(String acceptLanguage) {
    return validationFailure(
        new CaptchaVerifyResponse(false, null, null),
        ErrorCodes.REQUEST_VALIDATION_FAILED,
        CAPTCHA_ANSWER_TARGET);
  }

  public MutationResponse<CaptchaWorkflowResultResponse<CaptchaVerifyResponse>>
      missingChallengeIdResponse(String acceptLanguage) {
    return validationFailure(
        new CaptchaVerifyResponse(false, null, null),
        ErrorCodes.REQUEST_VALIDATION_FAILED,
        CAPTCHA_CHALLENGE_ID_TARGET);
  }

  public MutationResponse<CaptchaWorkflowResultResponse<CaptchaVerifyResponse>>
      invalidChallengeIdResponse(String acceptLanguage) {
    return validationFailure(
        new CaptchaVerifyResponse(false, null, null),
        ErrorCodes.REQUEST_VALIDATION_FAILED,
        CAPTCHA_CHALLENGE_ID_TARGET);
  }

  public MutationResponse<CaptchaWorkflowResultResponse<CaptchaRefreshResponse>>
      invalidRefreshChallengeIdResponse(String acceptLanguage) {
    ApiError error =
        ApiError.field(
            ErrorCodes.REQUEST_VALIDATION_FAILED,
            null,
            List.of(CAPTCHA_CHALLENGE_ID_TARGET),
            SOURCE_SERVER);
    return MutationResponse.failure(ApiStatus.VALIDATION_FAILED, wrap(null), List.of(error));
  }

  private MutationResponse<CaptchaWorkflowResultResponse<CaptchaVerifyResponse>> failure(
      VerifyCaptchaChallengeResult result, String errorCode, String target) {
    return validationFailure(toVerifyResponse(result), errorCode, target);
  }

  private MutationResponse<CaptchaWorkflowResultResponse<CaptchaVerifyResponse>> validationFailure(
      CaptchaVerifyResponse response, String errorCode, String target) {
    ApiError error = ApiError.field(errorCode, null, List.of(target), SOURCE_SERVER);
    return MutationResponse.failure(ApiStatus.VALIDATION_FAILED, wrap(response), List.of(error));
  }

  private <T> CaptchaWorkflowResultResponse<T> wrap(T data) {
    return new CaptchaWorkflowResultResponse<>(WorkflowNextStep.NONE, data);
  }

  private CaptchaChallengeResponse toChallengeResponse(CreateCaptchaChallengeResult result) {
    Objects.requireNonNull(result, RESULT_NOT_NULL);
    return new CaptchaChallengeResponse(
        result.captchaChallengeId().value(),
        result.image().imageBase64(),
        result.image().mimeType(),
        result.expiresInSeconds());
  }

  private CaptchaVerifyResponse toVerifyResponse(VerifyCaptchaChallengeResult result) {
    Objects.requireNonNull(result, RESULT_NOT_NULL);
    return new CaptchaVerifyResponse(
        result.verified(),
        result.captchaChallengeId() == null ? null : result.captchaChallengeId().value(),
        result.remainingAttempts());
  }

  private CaptchaRefreshResponse toRefreshResponse(RefreshCaptchaChallengeResult result) {
    Objects.requireNonNull(result, RESULT_NOT_NULL);
    return new CaptchaRefreshResponse(
        result.captchaChallengeId().value(),
        result.image().imageBase64(),
        result.image().mimeType(),
        result.expiresInSeconds());
  }
}
