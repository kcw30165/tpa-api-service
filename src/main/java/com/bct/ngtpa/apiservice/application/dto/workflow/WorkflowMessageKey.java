package com.bct.ngtpa.apiservice.application.dto.workflow;

public record WorkflowMessageKey(String value) {
    public WorkflowMessageKey {
        value = WorkflowKeyValidator.validate(value, "Invalid workflow message key");
    }
}
