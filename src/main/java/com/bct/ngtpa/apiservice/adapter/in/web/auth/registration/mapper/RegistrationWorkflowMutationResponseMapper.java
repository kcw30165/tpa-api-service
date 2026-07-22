package com.bct.ngtpa.apiservice.adapter.in.web.auth.registration.mapper;

import com.bct.ngtpa.apiservice.adapter.in.web.auth.registration.dto.RegistrationWorkflowResultResponse;
import com.bct.ngtpa.apiservice.adapter.in.web.response.MutationResponse;
import com.bct.ngtpa.apiservice.adapter.in.web.workflow.WorkflowMutationResponseMapper;
import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowResult;
import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowResultPayload;
import java.util.Objects;

final class RegistrationWorkflowMutationResponseMapper {
    private final WorkflowMutationResponseMapper delegate;

    RegistrationWorkflowMutationResponseMapper() {
        this(new WorkflowMutationResponseMapper());
    }

    RegistrationWorkflowMutationResponseMapper(WorkflowMutationResponseMapper delegate) {
        this.delegate = Objects.requireNonNull(delegate, "delegate must not be null");
    }

    <T> MutationResponse<RegistrationWorkflowResultResponse<T>> toMutationResponse(WorkflowResult<T> result) {
        MutationResponse<WorkflowResultPayload<T>> response = delegate.toMutationResponse(result);
        RegistrationWorkflowResultResponse<T> payload = response.result() == null
                ? null
                : new RegistrationWorkflowResultResponse<>(response.result().nextStep(), response.result().data());
        return new MutationResponse<>(
                response.success(),
                response.status(),
                payload,
                response.messages(),
                response.errors());
    }
}