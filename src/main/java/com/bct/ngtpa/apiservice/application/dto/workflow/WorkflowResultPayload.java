package com.bct.ngtpa.apiservice.application.dto.workflow;

import java.util.Objects;

public record WorkflowResultPayload<T>(WorkflowNextStep nextStep, T data, WorkflowPageRef pageRef) {
    public WorkflowResultPayload {
        nextStep = nextStep == null ? WorkflowNextStep.NONE : nextStep;
    }
}
