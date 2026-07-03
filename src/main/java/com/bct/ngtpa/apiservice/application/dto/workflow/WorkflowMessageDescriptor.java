package com.bct.ngtpa.apiservice.application.dto.workflow;

import java.util.Objects;

public record WorkflowMessageDescriptor(WorkflowMessageType type, String code, String message, String target) {
    public WorkflowMessageDescriptor {
        type = type == null ? WorkflowMessageType.INFO : type;
        Objects.requireNonNull(code, "code must not be null");
        if (code.isBlank()) {
            throw new IllegalArgumentException("code must not be blank");
        }
        target = target == null || target.isBlank() ? "PAGE" : target;
    }
}
