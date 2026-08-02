package com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.complete;

import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowErrorDescriptor;
import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowNextStep;
import java.util.List;

public record CompleteForgotPasswordResult(
        CompleteForgotPasswordStatus status,
        WorkflowNextStep nextStep,
        boolean clearAuthFlowCookie,
        boolean clearXsrfCookie,
        List<WorkflowErrorDescriptor> errors) {
    public CompleteForgotPasswordResult {
        nextStep = nextStep == null ? WorkflowNextStep.NONE : nextStep;
        errors = errors == null ? List.of() : List.copyOf(errors);
    }
}