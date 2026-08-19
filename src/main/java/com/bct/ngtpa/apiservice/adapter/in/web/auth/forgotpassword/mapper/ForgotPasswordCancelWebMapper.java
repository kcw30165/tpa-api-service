package com.bct.ngtpa.apiservice.adapter.in.web.auth.forgotpassword.mapper;

import com.bct.ngtpa.apiservice.adapter.in.web.auth.forgotpassword.dto.ForgotPasswordCancelDataResponse;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.forgotpassword.dto.ForgotPasswordWorkflowResultResponse;
import com.bct.ngtpa.apiservice.adapter.in.web.response.ApiError;
import com.bct.ngtpa.apiservice.adapter.in.web.response.ApiStatus;
import com.bct.ngtpa.apiservice.adapter.in.web.response.MutationResponse;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionId;
import com.bct.ngtpa.apiservice.application.dto.workflow.ForgotPasswordErrorKey;
import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowNextStep;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.cancel.CancelForgotPasswordCommand;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.cancel.CancelForgotPasswordResult;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.cancel.CancelForgotPasswordStatus;
import java.util.List;
import java.util.Objects;

/** Maps the Forgot Password cancel result to the approved mutation workflow contract. */
public final class ForgotPasswordCancelWebMapper {

  public CancelForgotPasswordCommand toCommand(
      String sessionId, String csrfToken, String origin, String referer, String requestId) {
    return new CancelForgotPasswordCommand(
        new AuthFlowSessionId(sessionId.trim()),
        trim(csrfToken),
        trim(origin),
        trim(referer),
        trim(requestId));
  }

  public MutationResponse<ForgotPasswordWorkflowResultResponse<ForgotPasswordCancelDataResponse>>
      toResponse(CancelForgotPasswordResult result) {
    Objects.requireNonNull(result, "result must not be null");
    if (result.successfulCleanup()) {
      return MutationResponse.success(ApiStatus.SUCCESS, payload(), List.of());
    }
    ApiStatus status =
        result.status() == CancelForgotPasswordStatus.CSRF_INVALID
                || result.status() == CancelForgotPasswordStatus.INVALID_AUTH_FLOW
            ? ApiStatus.VALIDATION_FAILED
            : ApiStatus.SYSTEM_ERROR;
    List<ApiError> errors =
        result.errors().stream()
            .map(
                error ->
                    new ApiError(
                        error.type().name(),
                        error.code(),
                        null,
                        sanitizeTargets(error.targets()),
                        error.severity().name(),
                        error.source()))
            .toList();
    return MutationResponse.failure(status, payload(), errors);
  }

  public MutationResponse<ForgotPasswordWorkflowResultResponse<ForgotPasswordCancelDataResponse>>
      missingSession() {
    return MutationResponse.success(ApiStatus.SUCCESS, payload(), List.of());
  }

  public MutationResponse<ForgotPasswordWorkflowResultResponse<ForgotPasswordCancelDataResponse>>
      missingCsrf() {
    return failure(ApiStatus.VALIDATION_FAILED, ForgotPasswordErrorKey.CSRF_INVALID.value());
  }

  public MutationResponse<ForgotPasswordWorkflowResultResponse<ForgotPasswordCancelDataResponse>>
      csrfInvalid() {
    return failure(ApiStatus.VALIDATION_FAILED, ForgotPasswordErrorKey.CSRF_INVALID.value());
  }

  public MutationResponse<ForgotPasswordWorkflowResultResponse<ForgotPasswordCancelDataResponse>>
      systemUnavailable() {
    return failure(ApiStatus.SYSTEM_ERROR, ForgotPasswordErrorKey.SYSTEM_UNAVAILABLE.value());
  }

  private MutationResponse<ForgotPasswordWorkflowResultResponse<ForgotPasswordCancelDataResponse>>
      failure(ApiStatus status, String code) {
    ApiError error = ApiError.form(code, null, List.of(), "BFF");
    return MutationResponse.failure(status, payload(), List.of(error));
  }

  private ForgotPasswordWorkflowResultResponse<ForgotPasswordCancelDataResponse> payload() {
    return new ForgotPasswordWorkflowResultResponse<>(
        WorkflowNextStep.RETURN_TO_LOGIN, new ForgotPasswordCancelDataResponse());
  }

  private List<String> sanitizeTargets(List<String> targets) {
    return targets == null
        ? List.of()
        : targets.stream()
            .filter(Objects::nonNull)
            .map(String::trim)
            .filter(target -> !target.isEmpty())
            .filter(target -> !target.equalsIgnoreCase("PAGE"))
            .toList();
  }

  private String trim(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }
}
