package com.bct.ngtpa.apiservice.adapter.in.web.auth.forgotpassword.mapper;

import com.bct.ngtpa.apiservice.adapter.in.web.auth.forgotpassword.dto.ForgotPasswordCompleteDataResponse;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.forgotpassword.dto.ForgotPasswordCompleteRequest;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.forgotpassword.dto.ForgotPasswordWorkflowResultResponse;
import com.bct.ngtpa.apiservice.adapter.in.web.response.ApiError;
import com.bct.ngtpa.apiservice.adapter.in.web.response.ApiStatus;
import com.bct.ngtpa.apiservice.adapter.in.web.response.MutationResponse;
import com.bct.ngtpa.apiservice.application.dto.ActorUserRole;
import com.bct.ngtpa.apiservice.application.dto.auth.forgotpassword.idempotency.ForgotPasswordIdempotencyKey;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionId;
import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowNextStep;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.complete.CompleteForgotPasswordCommand;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.complete.CompleteForgotPasswordResult;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.complete.CompleteForgotPasswordStatus;
import java.time.Duration;
import java.util.List;
import java.util.Objects;

public final class ForgotPasswordCompleteWebMapper {
  private final Duration idempotencyTtl;

  public ForgotPasswordCompleteWebMapper() {
    this(Duration.ofSeconds(300));
  }

  public ForgotPasswordCompleteWebMapper(Duration idempotencyTtl) {
    this.idempotencyTtl = Objects.requireNonNull(idempotencyTtl, "idempotencyTtl must not be null");
  }

  public CompleteForgotPasswordCommand toCommand(
      ForgotPasswordCompleteRequest request,
      String sessionId,
      String csrfToken,
      String idempotencyKey,
      String origin,
      String referer,
      String language,
      String requestId) {
    return new CompleteForgotPasswordCommand(
        new AuthFlowSessionId(sessionId.trim()),
        csrfToken.trim(),
        new ForgotPasswordIdempotencyKey(idempotencyKey.trim()),
        request == null ? null : request.password(),
        request == null ? null : request.confirmPassword(),
        clean(origin),
        clean(referer),
        clean(language),
        clean(requestId),
        idempotencyTtl,
        ActorUserRole.ROLE_USER_MBR);
  }

  public MutationResponse<ForgotPasswordWorkflowResultResponse<ForgotPasswordCompleteDataResponse>>
      toResponse(CompleteForgotPasswordResult result) {
    if (result == null) {
      return systemUnavailable();
    }
    ForgotPasswordWorkflowResultResponse<ForgotPasswordCompleteDataResponse> payload =
        new ForgotPasswordWorkflowResultResponse<>(
            result.nextStep(), new ForgotPasswordCompleteDataResponse());
    if (result.status() == CompleteForgotPasswordStatus.SUCCESS) {
      return MutationResponse.success(ApiStatus.SUCCESS, payload, List.of());
    }
    ApiStatus status =
        switch (result.status()) {
          case VALIDATION_FAILED, INVALID_AUTH_FLOW, CSRF_INVALID, IDEMPOTENCY_KEY_REUSED ->
              ApiStatus.VALIDATION_FAILED;
          case DUPLICATE_IN_PROGRESS, BUSINESS_REJECTED -> ApiStatus.BUSINESS_REJECTED;
          case UNKNOWN_OUTCOME, SYSTEM_FAILED -> ApiStatus.SYSTEM_ERROR;
          case SUCCESS -> ApiStatus.SUCCESS;
        };
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
    return MutationResponse.failure(status, payload, errors);
  }

  public MutationResponse<ForgotPasswordWorkflowResultResponse<ForgotPasswordCompleteDataResponse>>
      missingAuthFlowCookie() {
    return failure(
        ApiStatus.VALIDATION_FAILED,
        WorkflowNextStep.NONE,
        "err.forgot-password.authFlow.invalid",
        null);
  }

  public MutationResponse<ForgotPasswordWorkflowResultResponse<ForgotPasswordCompleteDataResponse>>
      missingCsrfToken() {
    return failure(
        ApiStatus.VALIDATION_FAILED,
        WorkflowNextStep.NONE,
        "err.forgot-password.csrf.invalid",
        null);
  }

  public MutationResponse<ForgotPasswordWorkflowResultResponse<ForgotPasswordCompleteDataResponse>>
      missingIdempotencyKey() {
    return failure(
        ApiStatus.VALIDATION_FAILED,
        WorkflowNextStep.NONE,
        "err.workflow.idempotency.missing",
        "Idempotency-Key");
  }

  public MutationResponse<ForgotPasswordWorkflowResultResponse<ForgotPasswordCompleteDataResponse>>
      invalidRequest() {
    return failure(
        ApiStatus.VALIDATION_FAILED,
        WorkflowNextStep.PASSWORD_SETUP,
        "err.forgot-password.complete.requestInvalid",
        null);
  }

  public MutationResponse<ForgotPasswordWorkflowResultResponse<ForgotPasswordCompleteDataResponse>>
      systemUnavailable() {
    return failure(ApiStatus.SYSTEM_ERROR, WorkflowNextStep.NONE, "err.system.unavailable", null);
  }

  private MutationResponse<ForgotPasswordWorkflowResultResponse<ForgotPasswordCompleteDataResponse>>
      failure(ApiStatus status, WorkflowNextStep nextStep, String code, String target) {
    return MutationResponse.failure(
        status,
        new ForgotPasswordWorkflowResultResponse<>(
            nextStep, new ForgotPasswordCompleteDataResponse()),
        List.of(
            ApiError.form(
                code, null, sanitizeTargets(target == null ? List.of() : List.of(target)), "BFF")));
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

  private String clean(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }
}
