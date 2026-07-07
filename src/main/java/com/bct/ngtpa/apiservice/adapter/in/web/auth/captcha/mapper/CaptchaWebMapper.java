package com.bct.ngtpa.apiservice.adapter.in.web.auth.captcha.mapper;

import com.bct.ngtpa.apiservice.adapter.in.web.auth.captcha.dto.CaptchaChallengeResponse;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.captcha.dto.CaptchaRefreshResponse;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.captcha.dto.CaptchaVerifyResponse;
import com.bct.ngtpa.apiservice.adapter.in.web.response.ApiError;
import com.bct.ngtpa.apiservice.adapter.in.web.response.ApiStatus;
import com.bct.ngtpa.apiservice.adapter.in.web.response.MutationResponse;
import com.bct.ngtpa.apiservice.adapter.in.web.support.RequestLanguageResolver;
import com.bct.ngtpa.apiservice.application.dto.auth.captcha.CaptchaVerificationOutcome;
import com.bct.ngtpa.apiservice.application.dto.auth.captcha.CreateCaptchaChallengeResult;
import com.bct.ngtpa.apiservice.application.dto.auth.captcha.RefreshCaptchaChallengeResult;
import com.bct.ngtpa.apiservice.application.dto.auth.captcha.VerifyCaptchaChallengeResult;
import com.bct.ngtpa.apiservice.application.dto.workflow.RegistrationWorkflowErrorKeys;
import com.bct.ngtpa.apiservice.shared.error.ErrorMessageResolver;
import java.util.List;
import java.util.Objects;

public class CaptchaWebMapper {

    private static final String SOURCE_SERVER = "SERVER";
    private static final String CAPTCHA_ANSWER_TARGET = "captchaAnswer";
    private static final String CAPTCHA_CHALLENGE_ID_TARGET = "captchaChallengeId";

    private final ErrorMessageResolver errorMessageResolver;

    public CaptchaWebMapper(ErrorMessageResolver errorMessageResolver) {
        this.errorMessageResolver = Objects.requireNonNull(errorMessageResolver, "errorMessageResolver must not be null");
    }

    public MutationResponse<CaptchaChallengeResponse> toChallengeMutationResponse(CreateCaptchaChallengeResult result) {
        return MutationResponse.success(ApiStatus.SUCCESS, toChallengeResponse(result), List.of());
    }

    public MutationResponse<CaptchaVerifyResponse> toVerifyMutationResponse(
            VerifyCaptchaChallengeResult result,
            String acceptLanguage) {
        Objects.requireNonNull(result, "result must not be null");
        return switch (result.outcome()) {
            case VERIFIED -> MutationResponse.success(ApiStatus.SUCCESS, toVerifyResponse(result), List.of());
            case INVALID -> failure(result, acceptLanguage,
                    RegistrationWorkflowErrorKeys.REGISTRATION_CAPTCHA_INVALID.value(), CAPTCHA_ANSWER_TARGET);
            case EXPIRED -> failure(result, acceptLanguage,
                    RegistrationWorkflowErrorKeys.REGISTRATION_CAPTCHA_EXPIRED.value(), CAPTCHA_CHALLENGE_ID_TARGET);
            case LOCKED -> failure(result, acceptLanguage,
                    RegistrationWorkflowErrorKeys.REGISTRATION_CAPTCHA_LOCKED.value(), CAPTCHA_CHALLENGE_ID_TARGET);
            case CONSUMED -> failure(result, acceptLanguage,
                    RegistrationWorkflowErrorKeys.REGISTRATION_CAPTCHA_CONSUMED.value(), CAPTCHA_CHALLENGE_ID_TARGET);
            case REFRESHED -> failure(result, acceptLanguage,
                    RegistrationWorkflowErrorKeys.REGISTRATION_CAPTCHA_REFRESH_FAILED.value(), CAPTCHA_CHALLENGE_ID_TARGET);
            case NOT_FOUND -> failure(result, acceptLanguage,
                    RegistrationWorkflowErrorKeys.REGISTRATION_CAPTCHA_NOT_FOUND.value(), CAPTCHA_CHALLENGE_ID_TARGET);
        };
    }

    public MutationResponse<CaptchaRefreshResponse> toRefreshMutationResponse(RefreshCaptchaChallengeResult result) {
        return MutationResponse.success(ApiStatus.SUCCESS, toRefreshResponse(result), List.of());
    }

    public MutationResponse<CaptchaVerifyResponse> missingAnswerResponse(String acceptLanguage) {
        return validationFailure(
                new CaptchaVerifyResponse(false, null, null),
                RegistrationWorkflowErrorKeys.REGISTRATION_CAPTCHA_REQUIRED.value(),
                CAPTCHA_ANSWER_TARGET,
                acceptLanguage);
    }

    public MutationResponse<CaptchaVerifyResponse> missingChallengeIdResponse(String acceptLanguage) {
        return validationFailure(
                new CaptchaVerifyResponse(false, null, null),
                RegistrationWorkflowErrorKeys.REGISTRATION_CAPTCHA_REQUIRED.value(),
                CAPTCHA_CHALLENGE_ID_TARGET,
                acceptLanguage);
    }

    public MutationResponse<CaptchaVerifyResponse> invalidChallengeIdResponse(String acceptLanguage) {
        return validationFailure(
                new CaptchaVerifyResponse(false, null, null),
                RegistrationWorkflowErrorKeys.REGISTRATION_CAPTCHA_INVALID.value(),
                CAPTCHA_CHALLENGE_ID_TARGET,
                acceptLanguage);
    }

        public MutationResponse<CaptchaRefreshResponse> invalidRefreshChallengeIdResponse(String acceptLanguage) {
                ApiError error = ApiError.field(
                                RegistrationWorkflowErrorKeys.REGISTRATION_CAPTCHA_INVALID.value(),
                                resolveMessage(RegistrationWorkflowErrorKeys.REGISTRATION_CAPTCHA_INVALID.value(), acceptLanguage),
                                List.of(CAPTCHA_CHALLENGE_ID_TARGET),
                                SOURCE_SERVER);
                return MutationResponse.failure(ApiStatus.VALIDATION_FAILED, null, List.of(error));
        }

    private MutationResponse<CaptchaVerifyResponse> failure(
            VerifyCaptchaChallengeResult result,
            String acceptLanguage,
            String errorCode,
            String target) {
        return validationFailure(toVerifyResponse(result), errorCode, target, acceptLanguage);
    }

    private MutationResponse<CaptchaVerifyResponse> validationFailure(
            CaptchaVerifyResponse response,
            String errorCode,
            String target,
            String acceptLanguage) {
        ApiError error = ApiError.field(
                errorCode,
                resolveMessage(errorCode, acceptLanguage),
                List.of(target),
                SOURCE_SERVER);
        return MutationResponse.failure(ApiStatus.VALIDATION_FAILED, response, List.of(error));
    }

    private CaptchaChallengeResponse toChallengeResponse(CreateCaptchaChallengeResult result) {
        Objects.requireNonNull(result, "result must not be null");
        return new CaptchaChallengeResponse(
                result.captchaChallengeId().value(),
                result.image().imageBase64(),
                result.image().mimeType(),
                result.expiresInSeconds());
    }

    private CaptchaVerifyResponse toVerifyResponse(VerifyCaptchaChallengeResult result) {
        Objects.requireNonNull(result, "result must not be null");
        return new CaptchaVerifyResponse(
                result.verified(),
                result.captchaChallengeId() == null ? null : result.captchaChallengeId().value(),
                result.remainingAttempts());
    }

    private CaptchaRefreshResponse toRefreshResponse(RefreshCaptchaChallengeResult result) {
        Objects.requireNonNull(result, "result must not be null");
        return new CaptchaRefreshResponse(
                result.captchaChallengeId().value(),
                result.image().imageBase64(),
                result.image().mimeType(),
                result.expiresInSeconds());
    }

    private String resolveMessage(String errorCode, String acceptLanguage) {
        return errorMessageResolver.resolve(
                errorCode,
                RequestLanguageResolver.normalize(acceptLanguage),
                null,
                null,
                null);
    }
}