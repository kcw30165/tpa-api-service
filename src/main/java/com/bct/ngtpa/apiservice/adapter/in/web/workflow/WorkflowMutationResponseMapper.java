package com.bct.ngtpa.apiservice.adapter.in.web.workflow;

import com.bct.ngtpa.apiservice.adapter.in.web.response.ApiError;
import com.bct.ngtpa.apiservice.adapter.in.web.response.ApiMessage;
import com.bct.ngtpa.apiservice.adapter.in.web.response.ApiStatus;
import com.bct.ngtpa.apiservice.adapter.in.web.response.MutationResponse;
import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowErrorDescriptor;
import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowMessageDescriptor;
import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowResult;
import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowResultPayload;
import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowResultStatus;
import java.util.List;
import java.util.Objects;

public class WorkflowMutationResponseMapper {

  public <T> MutationResponse<WorkflowResultPayload<T>> toMutationResponse(
      WorkflowResult<T> result) {
    Objects.requireNonNull(result, "result must not be null");
    var payload = new WorkflowResultPayload<>(result.nextStep(), result.payload());
    var apiStatus = toApiStatus(result.status());
    var messages = result.messages().stream().map(this::toApiMessage).toList();
    var errors = result.errors().stream().map(this::toApiError).toList();
    if (result.isSuccessLike()) {
      return MutationResponse.success(apiStatus, payload, messages);
    }
    return MutationResponse.failure(apiStatus, payload, errors);
  }

  private ApiStatus toApiStatus(WorkflowResultStatus status) {
    return switch (status) {
      case SUCCESS, WAITING -> ApiStatus.SUCCESS;
      case UPDATED -> ApiStatus.UPDATED;
      case VALIDATION_FAILED -> ApiStatus.VALIDATION_FAILED;
      case BUSINESS_FAILED -> ApiStatus.BUSINESS_REJECTED;
      case SYSTEM_FAILED -> ApiStatus.SYSTEM_ERROR;
    };
  }

  private ApiMessage toApiMessage(WorkflowMessageDescriptor descriptor) {
    return switch (descriptor.type()) {
      case SUCCESS ->
          ApiMessage.success(descriptor.code(), descriptor.message(), descriptor.target());
      case INFO -> ApiMessage.info(descriptor.code(), descriptor.message(), descriptor.target());
      case WARNING ->
          ApiMessage.warning(descriptor.code(), descriptor.message(), descriptor.target());
    };
  }

  private ApiError toApiError(WorkflowErrorDescriptor descriptor) {
    return new ApiError(
        descriptor.type().name(),
        descriptor.code(),
        descriptor.message(),
        descriptor.targets() == null ? List.of() : descriptor.targets(),
        descriptor.severity().name(),
        descriptor.source());
  }
}
