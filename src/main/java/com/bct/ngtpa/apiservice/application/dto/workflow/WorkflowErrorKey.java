package com.bct.ngtpa.apiservice.application.dto.workflow;

/** Common contract implemented by registration-owned and shared workflow errors. */
public interface WorkflowErrorKey {
    String value();

    static WorkflowErrorKey of(String value) {
        return new DynamicWorkflowErrorKey(
                WorkflowKeyValidator.validate(value, "Invalid workflow error key"));
    }

    record DynamicWorkflowErrorKey(String value) implements WorkflowErrorKey {
        public DynamicWorkflowErrorKey {
            value = WorkflowKeyValidator.validate(
                    value,
                    "Invalid workflow error key");
        }
    }
}
