package com.bct.ngtpa.apiservice.adapter.in.web.auth.login.cancel;

import com.bct.ngtpa.apiservice.adapter.in.web.auth.login.dto.LoginWorkflowResultResponse;
import com.bct.ngtpa.apiservice.adapter.in.web.response.*;
import com.bct.ngtpa.apiservice.application.dto.workflow.*;
import com.bct.ngtpa.apiservice.application.usecase.auth.login.cancel.*;
import java.util.*;

public final class CancelLoginWebMapper {
  public MutationResponse<LoginWorkflowResultResponse<CancelLoginDataResponse>> toMutationResponse(
      CancelLoginResult r) {
    var payload =
        new LoginWorkflowResultResponse<>(
            WorkflowNextStep.RETURN_TO_LOGIN, new CancelLoginDataResponse(List.of()));
    if (r.successfulCleanup())
      return MutationResponse.success(ApiStatus.SUCCESS, payload, List.of());
    ApiStatus status =
        r.status() == CancelLoginStatus.SYSTEM_FAILED
            ? ApiStatus.SYSTEM_ERROR
            : ApiStatus.VALIDATION_FAILED;
    List<ApiError> errors =
        r.errors().stream()
            .map(
                e ->
                    new ApiError(
                        e.type().name(),
                        e.code(),
                        null,
                        e.targets(),
                        e.severity().name(),
                        e.source()))
            .toList();
    return MutationResponse.failure(status, payload, errors);
  }

  public MutationResponse<LoginWorkflowResultResponse<CancelLoginDataResponse>> missingCsrf() {
    var payload =
        new LoginWorkflowResultResponse<>(
            WorkflowNextStep.RETURN_TO_LOGIN, new CancelLoginDataResponse(List.of()));
    return MutationResponse.failure(
        ApiStatus.VALIDATION_FAILED,
        payload,
        List.of(ApiError.form(LoginErrorKey.CSRF_INVALID.value(), null, List.of("PAGE"), "BFF")));
  }

  public MutationResponse<LoginWorkflowResultResponse<CancelLoginDataResponse>> missingSession() {
    return MutationResponse.success(
        ApiStatus.SUCCESS,
        new LoginWorkflowResultResponse<>(
            WorkflowNextStep.RETURN_TO_LOGIN, new CancelLoginDataResponse(List.of())),
        List.of());
  }
}
