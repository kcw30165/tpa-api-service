package com.bct.ngtpa.apiservice.application.dto.workflow;

import java.util.List;
import java.util.Objects;

public record WorkflowResult<T>(
        WorkflowResultStatus status,
        WorkflowNextStep nextStep,
        T payload,
        List<WorkflowMessageDescriptor> messages,
        List<WorkflowErrorDescriptor> errors) {

    public WorkflowResult {
        Objects.requireNonNull(status, "status must not be null");
        nextStep = nextStep == null ? WorkflowNextStep.NONE : nextStep;
        messages = messages == null ? List.of() : List.copyOf(messages);
        errors = errors == null ? List.of() : List.copyOf(errors);
    }

    public static <T> WorkflowResult<T> success(WorkflowNextStep nextStep, T payload) {
        return new WorkflowResult<>(WorkflowResultStatus.SUCCESS, nextStep, payload, List.of(), List.of());
    }

    public static <T> WorkflowResult<T> failure(
            WorkflowResultStatus status,
            WorkflowNextStep nextStep,
            List<WorkflowErrorDescriptor> errors) {
        if (status == WorkflowResultStatus.SUCCESS || status == WorkflowResultStatus.UPDATED
                || status == WorkflowResultStatus.WAITING) {
            throw new IllegalArgumentException("failure status must not be success-like");
        }
        return new WorkflowResult<>(status, nextStep, null, List.of(), errors);
    }

    public static <T> WorkflowResult<T> duplicateInProgress(WorkflowNextStep nextStep) {
        return new WorkflowResult<>(WorkflowResultStatus.WAITING, nextStep, null, List.of(), List.of());
    }

    public boolean isSuccessLike() {
        return status == WorkflowResultStatus.SUCCESS
                || status == WorkflowResultStatus.UPDATED
                || status == WorkflowResultStatus.WAITING;
    }

    @Override
    public String toString() {
        return "WorkflowResult[status=" + status
                + ", nextStep=" + nextStep
                + ", messageCount=" + messages.size()
                + ", errorCount=" + errors.size()
                + "]";
    }
}
