package com.bct.ngtpa.apiservice.adapter.in.web.auth.captcha.dto;

import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowNextStep;

public record CaptchaWorkflowResultResponse<T>(WorkflowNextStep nextStep, T data) {
    public CaptchaWorkflowResultResponse {
        nextStep = nextStep == null ? WorkflowNextStep.NONE : nextStep;
    }
}