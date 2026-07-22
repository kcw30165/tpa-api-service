package com.bct.ngtpa.apiservice.adapter.in.web.auth.registration.dto;

import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowNextStep;

public record RegistrationWorkflowResultResponse<T>(WorkflowNextStep nextStep, T data) {
    public RegistrationWorkflowResultResponse {
        nextStep = nextStep == null ? WorkflowNextStep.NONE : nextStep;
    }

    public Integer retryAfterSeconds() {
        if (data == null) {
            return null;
        }
        try {
            Object value = data.getClass().getMethod("retryAfterSeconds").invoke(data);
            return value instanceof Integer number ? number : null;
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }
}