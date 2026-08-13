package com.bct.ngtpa.apiservice.adapter.in.web.auth.usernamerecovery;

import com.bct.ngtpa.apiservice.adapter.in.web.response.ApiError;
import com.bct.ngtpa.apiservice.adapter.in.web.response.ApiMessage;
import com.bct.ngtpa.apiservice.adapter.in.web.response.ApiStatus;
import com.bct.ngtpa.apiservice.adapter.in.web.response.HttpStatusClassificationOverride;
import com.bct.ngtpa.apiservice.adapter.in.web.response.MutationResponse;
import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowMessageKey;
import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowNextStep;
import com.bct.ngtpa.apiservice.shared.error.ErrorCodes;
import java.util.List;
import java.util.Objects;

/**
 * Shared Task 023 mapping helpers for Username Recovery feature response
 * policies: committed message-label constants, approved target/source
 * identifiers, the typed policy wrapper, the failure envelope builder, and
 * ApiMessage/ApiError factories that reuse the exact public keys and envelope
 * shapes the shared transport helpers own.
 *
 * <p>This class is framework-free: it imports no Spring, servlet, Reactor,
 * controller, cookie, adapter.out, config, or infrastructure types.
 */
public final class UsernameRecoveryWorkflowMapping {

    public static final WorkflowMessageKey MESSAGE_COMPLETE_REGISTRATION =
            new WorkflowMessageKey("label.usernamerecovery.complete.registration");
    public static final WorkflowMessageKey MESSAGE_REQUEST_ACCEPTED =
            new WorkflowMessageKey("label.usernamerecovery.request.accepted");
    public static final WorkflowMessageKey MESSAGE_RETURN_TO_LOGIN =
            new WorkflowMessageKey("label.return.to.login");

    public static final String TARGET_IDEMPOTENCY_KEY = "Idempotency-Key";
    public static final String TARGET_SCHEME_REF = "Scheme-Ref";
    public static final String TARGET_PAGE = "PAGE";

    public static final String SOURCE_BFF = "BFF";
    public static final String SOURCE_SERVER = "SERVER";

    private UsernameRecoveryWorkflowMapping() {
    }

    public static <T> UsernameRecoveryResponsePolicy<T> policy(MutationResponse<T> body) {
        return new UsernameRecoveryResponsePolicy<>(
                Objects.requireNonNull(body, "body must not be null"), null);
    }

    public static <T> UsernameRecoveryResponsePolicy<T> policy(
            MutationResponse<T> body, HttpStatusClassificationOverride override) {
        return new UsernameRecoveryResponsePolicy<>(
                Objects.requireNonNull(body, "body must not be null"), override);
    }

    public static <P> MutationResponse<UsernameRecoveryWorkflowResultResponse<P>> failureResponse(
            ApiStatus status, WorkflowNextStep nextStep, List<ApiError> errors) {
        return MutationResponse.failure(
                status, new UsernameRecoveryWorkflowResultResponse<>(nextStep, null), errors);
    }

    public static ApiMessage infoMessage(WorkflowMessageKey key) {
        Objects.requireNonNull(key, "message key must not be null");
        return ApiMessage.info(key.value(), null, null);
    }

    public static ApiError formError(String code, List<String> targets, String source) {
        return ApiError.form(
                Objects.requireNonNull(code, "code must not be null"), null, targets, source);
    }

    public static ApiError businessError(String code, List<String> targets, String source) {
        return ApiError.business(
                Objects.requireNonNull(code, "code must not be null"), null, targets, source);
    }

    public static ApiError systemError(String code, String source) {
        return ApiError.system(Objects.requireNonNull(code, "code must not be null"), null, source);
    }

    /**
     * Error matching the {@code CookieHttpResponses}/{@code CsrfHttpResponses}
     * envelope (the 401 status itself remains a Task 024 transport detail).
     */
    public static ApiError authenticationRequiredError() {
        return new ApiError(
                "AUTHENTICATION_REQUIRED",
                ErrorCodes.SECURITY_AUTHENTICATION_REQUIRED,
                null,
                List.of(TARGET_PAGE),
                "ERROR",
                SOURCE_SERVER);
    }
}
