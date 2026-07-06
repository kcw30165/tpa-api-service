package com.bct.ngtpa.apiservice.application.dto.workflow;

public record WorkflowErrorKey(String value) {
    public WorkflowErrorKey {
        value = WorkflowKeyValidator.validate(value, "Invalid workflow error key");
    }
}
