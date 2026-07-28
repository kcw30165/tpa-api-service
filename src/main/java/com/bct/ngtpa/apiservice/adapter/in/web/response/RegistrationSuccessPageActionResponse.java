package com.bct.ngtpa.apiservice.adapter.in.web.response;

import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowNextStep;

public record RegistrationSuccessPageActionResponse(
                String id,
                String type,
                WorkflowNextStep nextStep) {
}
