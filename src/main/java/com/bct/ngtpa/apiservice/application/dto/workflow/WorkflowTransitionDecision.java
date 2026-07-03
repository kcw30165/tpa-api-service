package com.bct.ngtpa.apiservice.application.dto.workflow;

import java.util.Objects;

public record WorkflowTransitionDecision(
        WorkflowStableStateRule rule,
        WorkflowStep currentStep,
        WorkflowNextStep nextStep,
        WorkflowFailureCategory failureCategory,
        WorkflowLogSeverity logSeverity) {
    public WorkflowTransitionDecision {
        Objects.requireNonNull(rule, "rule must not be null");
        nextStep = nextStep == null ? WorkflowNextStep.NONE : nextStep;
        if (failureCategory != null && logSeverity == null) {
            logSeverity = WorkflowFailureClassification.forCategory(failureCategory).logSeverity();
        }
    }

    @Override
    public String toString() {
        return "WorkflowTransitionDecision[rule=" + rule
                + ", currentStep=" + currentStep
                + ", nextStep=" + nextStep
                + ", failureCategory=" + failureCategory
                + ", logSeverity=" + logSeverity
                + "]";
    }
}
