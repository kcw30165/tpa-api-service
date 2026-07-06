package com.bct.ngtpa.apiservice.application.dto.workflow;

public final class WorkflowTransitionCheck {
    private WorkflowTransitionCheck() {
    }

    public static WorkflowTransitionDecision success(WorkflowStep currentStep, WorkflowNextStep nextStep) {
        return new WorkflowTransitionDecision(
                WorkflowStableStateRule.MOVE_TO_NEXT_STABLE_STATE,
                currentStep,
                nextStep,
                null,
                null);
    }

    public static WorkflowTransitionDecision recoverableFailure(
            WorkflowStep currentStep,
            WorkflowNextStep nextStep,
            WorkflowFailureCategory category) {
        return new WorkflowTransitionDecision(
                WorkflowStableStateRule.KEEP_CURRENT_STABLE_STATE,
                currentStep,
                nextStep,
                category,
                severity(category));
    }

    public static WorkflowTransitionDecision terminal(WorkflowStep currentStep, WorkflowNextStep nextStep) {
        return new WorkflowTransitionDecision(
                WorkflowStableStateRule.MOVE_TO_TERMINAL_STATE,
                currentStep,
                nextStep,
                null,
                null);
    }

    public static WorkflowTransitionDecision restartRequired(WorkflowFailureCategory category) {
        return new WorkflowTransitionDecision(
                WorkflowStableStateRule.RESTART_REQUIRED,
                null,
                WorkflowNextStep.RESTART_FLOW,
                category,
                severity(category));
    }

    public static WorkflowTransitionDecision restorePrevious(
            WorkflowStep previousStableStep,
            WorkflowNextStep nextStep,
            WorkflowFailureCategory category) {
        return new WorkflowTransitionDecision(
                WorkflowStableStateRule.RESTORE_PREVIOUS_STABLE_STATE,
                previousStableStep,
                nextStep,
                category,
                severity(category));
    }

    private static WorkflowLogSeverity severity(WorkflowFailureCategory category) {
        return category == null ? null : WorkflowSeverityMapper.severityFor(category);
    }
}
