package com.bct.ngtpa.apiservice.adapter.in.web.auth.registration.dto;

import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowNextStep;

public record RegistrationActionResponse(
        String id,
        String type,
        WorkflowNextStep nextStep) {
}