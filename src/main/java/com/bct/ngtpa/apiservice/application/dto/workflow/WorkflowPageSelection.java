package com.bct.ngtpa.apiservice.application.dto.workflow;

import java.util.Objects;

public record WorkflowPageSelection(
        WorkflowScenario scenario,
        WorkflowNextStep nextStep,
        WorkflowPageRef pageRef) {
    public WorkflowPageSelection {
        Objects.requireNonNull(scenario, "scenario must not be null");
        Objects.requireNonNull(nextStep, "nextStep must not be null");
    }

    public boolean hasPageRef() {
        return pageRef != null;
    }
}
