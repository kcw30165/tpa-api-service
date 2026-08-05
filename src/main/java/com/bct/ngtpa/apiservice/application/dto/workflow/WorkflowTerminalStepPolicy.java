package com.bct.ngtpa.apiservice.application.dto.workflow;

public final class WorkflowTerminalStepPolicy {
    private WorkflowTerminalStepPolicy() {
    }

    public static boolean isTerminalNextStep(WorkflowNextStep nextStep) {
        return nextStep == WorkflowNextStep.REGISTRATION_SUCCESS
                || nextStep == WorkflowNextStep.PASSWORD_RESET_SUCCESS
                || nextStep == WorkflowNextStep.RETURN_TO_LOGIN;
    }

    public static boolean isRestartStep(WorkflowNextStep nextStep) {
        return nextStep == WorkflowNextStep.RESTART_FLOW;
    }

    public static boolean isWaitStep(WorkflowNextStep nextStep) {
        return nextStep == WorkflowNextStep.WAIT;
    }
}
