package com.bct.ngtpa.apiservice.application.usecase.auth.registration.username;

import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowErrorDescriptor;
import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowMessageDescriptor;
import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowNextStep;
import java.util.List;

public record CheckRegistrationUsernameResult(
        CheckRegistrationUsernameResultStatus status,
        WorkflowNextStep nextStep,
        List<WorkflowMessageDescriptor> messages,
        List<WorkflowErrorDescriptor> errors) {
    public CheckRegistrationUsernameResult {
        messages = messages == null ? List.of() : List.copyOf(messages);
        errors = errors == null ? List.of() : List.copyOf(errors);
    }

    public static WorkflowNextStep nextStepFor(CheckRegistrationUsernameResultStatus status) {
        if (status == CheckRegistrationUsernameResultStatus.USERNAME_AVAILABLE) {
            return WorkflowNextStep.PASSWORD_SETUP;
        }
        return WorkflowNextStep.USERNAME_CHECK;
    }
}
