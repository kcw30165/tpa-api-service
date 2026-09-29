package com.bct.ngtpa.apiservice.adapter.in.web.auth.registration.mapper;

import com.bct.ngtpa.apiservice.adapter.in.web.auth.registration.dto.RegistrationStartRequest;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.registration.dto.RegistrationStartResponse;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.registration.dto.RegistrationStartWorkflowDataResponse;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.registration.dto.RegistrationWorkflowResultResponse;
import com.bct.ngtpa.apiservice.adapter.in.web.response.ApiMessage;
import com.bct.ngtpa.apiservice.adapter.in.web.response.ApiStatus;
import com.bct.ngtpa.apiservice.adapter.in.web.response.MutationResponse;
import com.bct.ngtpa.apiservice.adapter.in.web.workflow.WorkflowMutationResponseMapper;
import com.bct.ngtpa.apiservice.application.dto.auth.idempotency.RegistrationIdempotencyKey;
import com.bct.ngtpa.apiservice.application.dto.workflow.RegistrationMessageKey;
import com.bct.ngtpa.apiservice.application.dto.workflow.SharedWorkflowErrorKey;
import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowErrorDescriptor;
import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowErrorSeverity;
import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowErrorType;
import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowNextStep;
import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowResult;
import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowResultStatus;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.StartRegistrationCommand;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.StartRegistrationError;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.StartRegistrationResult;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.StartRegistrationResultStatus;
import java.time.Duration;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class RegistrationStartWebMapper {
  private static final String SOURCE_SERVER = "SERVER";
  private static final String TARGET_PAGE = "PAGE";

  private final Duration defaultIdempotencyTtl;
  private final RegistrationWorkflowMutationResponseMapper workflowMutationResponseMapper;

  public RegistrationStartWebMapper(Duration defaultIdempotencyTtl) {
    this(defaultIdempotencyTtl, new WorkflowMutationResponseMapper());
  }

  public RegistrationStartWebMapper(Duration defaultIdempotencyTtl, Object ignoredMessageResolver) {
    this(defaultIdempotencyTtl, new WorkflowMutationResponseMapper());
  }


  private RegistrationStartWebMapper(
      Duration defaultIdempotencyTtl,
      WorkflowMutationResponseMapper workflowMutationResponseMapper) {
    this.defaultIdempotencyTtl = java.util.Objects.requireNonNull(defaultIdempotencyTtl);
    this.workflowMutationResponseMapper =
        new RegistrationWorkflowMutationResponseMapper(
            java.util.Objects.requireNonNull(workflowMutationResponseMapper));
  }

  public StartRegistrationCommand toCommand(
      RegistrationStartRequest request,
      String idempotencyKey,
      String correlationId,
      String acceptLanguage,
      String authFlowSessionId) {
    return new StartRegistrationCommand(
        new RegistrationIdempotencyKey(idempotencyKey),
        request.identityType(),
        request.identityValue(),
        request.dateOfBirth(),
        request.email(),
        request.mobile(),
        request.captchaChallengeId(),
        authFlowSessionId,
        acceptLanguage,
        correlationId,
        defaultIdempotencyTtl);
  }

  public StartRegistrationCommand toCommand(
      RegistrationStartRequest request,
      String idempotencyKey,
      String correlationId,
      String acceptLanguage) {
    return toCommand(request, idempotencyKey, correlationId, acceptLanguage, null);
  }

  public RegistrationStartResponse toResponse(StartRegistrationResult result) {
    if (result == null || !isSuccess(result.status())) {
      return null;
    }
    return new RegistrationStartResponse(
        result.nextStep(),
        result.maskedDestination(),
        result.otpExpiresInSeconds() > 0 ? result.otpExpiresInSeconds() : null,
        result.resendCooldownSeconds() > 0 ? result.resendCooldownSeconds() : null,
        result.remainingResendAttempts() > 0 ? result.remainingResendAttempts() : null,
        result.csrfToken());
  }

  public RegistrationStartWorkflowDataResponse toWorkflowData(StartRegistrationResult result) {
    if (result == null || !isSuccess(result.status())) {
      return null;
    }
    return new RegistrationStartWorkflowDataResponse(
        result.maskedDestination(),
        result.otpExpiresInSeconds() > 0 ? result.otpExpiresInSeconds() : null,
        result.resendCooldownSeconds() > 0 ? result.resendCooldownSeconds() : null,
        result.remainingResendAttempts() > 0 ? result.remainingResendAttempts() : null,
        result.csrfToken());
  }

  public MutationResponse<RegistrationWorkflowResultResponse<RegistrationStartWorkflowDataResponse>>
      toMutationResponse(StartRegistrationResult result, String acceptLanguage) {
    logFatalIfNeeded(result);
    if (result != null && result.status() == StartRegistrationResultStatus.REGISTRATION_COMPLETED) {
      return MutationResponse.success(
          ApiStatus.SUCCESS,
          new RegistrationWorkflowResultResponse<>(WorkflowNextStep.RETURN_TO_LOGIN, null),
          List.of(ApiMessage.info(RegistrationMessageKey.COMPLETION.value(), null, TARGET_PAGE)));
    }
    var response =
        workflowMutationResponseMapper.toMutationResponse(toWorkflowResult(result, acceptLanguage));
    if (result != null
        && result.status() == StartRegistrationResultStatus.MESSAGE_DELIVERY_FAILED) {
      return new MutationResponse<>(
          false, ApiStatus.SYSTEM_ERROR, response.result(), response.messages(), response.errors());
    }
    return response;
  }

  public MutationResponse<RegistrationWorkflowResultResponse<RegistrationStartWorkflowDataResponse>>
      toMutationResponse(StartRegistrationResult result) {
    return toMutationResponse(result, null);
  }

  public MutationResponse<RegistrationWorkflowResultResponse<RegistrationStartWorkflowDataResponse>>
      missingIdempotencyKeyResponse(String acceptLanguage) {
    var code = SharedWorkflowErrorKey.IDEMPOTENCY_MISSING.value();
    var error =
        new WorkflowErrorDescriptor(
            WorkflowErrorType.FIELD,
            code,
            null,
            List.of("Idempotency-Key"),
            WorkflowErrorSeverity.ERROR,
            SOURCE_SERVER);
    return workflowMutationResponseMapper.toMutationResponse(
        new WorkflowResult<RegistrationStartWorkflowDataResponse>(
            WorkflowResultStatus.VALIDATION_FAILED,
            WorkflowNextStep.NONE,
            null,
            List.of(),
            List.of(error)));
  }

  public MutationResponse<RegistrationWorkflowResultResponse<RegistrationStartWorkflowDataResponse>>
      missingIdempotencyKeyResponse() {
    return missingIdempotencyKeyResponse(null);
  }

  WorkflowResult<RegistrationStartWorkflowDataResponse> toWorkflowResult(
      StartRegistrationResult result, String acceptLanguage) {
    if (result == null) {
      return new WorkflowResult<>(
          WorkflowResultStatus.SYSTEM_FAILED,
          WorkflowNextStep.NONE,
          null,
          List.of(),
          List.of(
              new WorkflowErrorDescriptor(
                  WorkflowErrorType.SYSTEM,
                  com.bct.ngtpa.apiservice.application.dto.workflow.SharedWorkflowErrorKey
                      .SYSTEM_UNAVAILABLE
                      .value(),
                  null,
                  List.of(),
                  WorkflowErrorSeverity.ERROR,
                  SOURCE_SERVER)));
    }

    WorkflowResultStatus workflowStatus = toWorkflowStatus(result.status());
    RegistrationStartWorkflowDataResponse response =
        isSuccess(result.status()) ? toWorkflowData(result) : null;
    return new WorkflowResult<>(
        workflowStatus,
        result.nextStep(),
        response,
        List.of(),
        result.errors().stream()
            .map(error -> toWorkflowError(error, result.status(), acceptLanguage))
            .toList());
  }

  private WorkflowErrorDescriptor toWorkflowError(
      StartRegistrationError error, StartRegistrationResultStatus status, String acceptLanguage) {
    String code = error.errorKey().value();
    String target = normalizeTarget(error.target());
    return new WorkflowErrorDescriptor(
        toWorkflowErrorType(status, target),
        code,
        null,
        target == null ? List.of() : List.of(target),
        WorkflowErrorSeverity.ERROR,
        SOURCE_SERVER);
  }

  private WorkflowErrorType toWorkflowErrorType(
      StartRegistrationResultStatus status, String target) {
    if (target != null && !TARGET_PAGE.equalsIgnoreCase(target)) {
      return WorkflowErrorType.FIELD;
    }
    return switch (status) {
      case MESSAGE_DELIVERY_FAILED -> WorkflowErrorType.DOWNSTREAM_SYSTEM;
      case SYSTEM_FAILED -> WorkflowErrorType.SYSTEM;
      case DOWNSTREAM_REJECTED -> WorkflowErrorType.DOWNSTREAM_BUSINESS;
      case VERIFICATION_FAILED, IDEMPOTENCY_KEY_REUSED -> WorkflowErrorType.BUSINESS;
      default -> WorkflowErrorType.FORM;
    };
  }

  private WorkflowResultStatus toWorkflowStatus(StartRegistrationResultStatus status) {
    return switch (status) {
      case STARTED, REGISTRATION_COMPLETED, COMPLETED_REPLAY -> WorkflowResultStatus.SUCCESS;
      case DUPLICATE_IN_PROGRESS -> WorkflowResultStatus.WAITING;
      case VALIDATION_FAILED -> WorkflowResultStatus.VALIDATION_FAILED;
      case VERIFICATION_FAILED, DOWNSTREAM_REJECTED, IDEMPOTENCY_KEY_REUSED ->
          WorkflowResultStatus.BUSINESS_FAILED;
      case MESSAGE_DELIVERY_FAILED, SYSTEM_FAILED -> WorkflowResultStatus.SYSTEM_FAILED;
    };
  }

  private boolean isSuccess(StartRegistrationResultStatus status) {
    return status == StartRegistrationResultStatus.STARTED
        || status == StartRegistrationResultStatus.REGISTRATION_COMPLETED
        || status == StartRegistrationResultStatus.DUPLICATE_IN_PROGRESS
        || status == StartRegistrationResultStatus.COMPLETED_REPLAY;
  }

  private String normalizeTarget(String target) {
    if (target == null || target.isBlank()) {
      return TARGET_PAGE;
    }
    return target.trim();
  }

  private void logFatalIfNeeded(StartRegistrationResult result) {
    if (result == null || result.status() == null) {
      return;
    }
    if (result.status() != StartRegistrationResultStatus.MESSAGE_DELIVERY_FAILED
        && result.status() != StartRegistrationResultStatus.SYSTEM_FAILED) {
      return;
    }
    log.error(
        "event=registration.start.failure severity=FATAL status={} errorCodes={}",
        result.status(),
        result.errors().stream().map(error -> error.errorKey().value()).toList());
  }
}
