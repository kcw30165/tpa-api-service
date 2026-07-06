package com.bct.ngtpa.apiservice.application.dto.workflow;

import java.util.Objects;

public record WorkflowResolvedPageAction(
        String id,
        String type,
        String label,
        WorkflowNextStep nextStep) {
    public WorkflowResolvedPageAction {
        Objects.requireNonNull(id, "id must not be null");
        if (id.isBlank()) {
            throw new IllegalArgumentException("id must not be blank");
        }
        Objects.requireNonNull(type, "type must not be null");
        if (type.isBlank()) {
            throw new IllegalArgumentException("type must not be blank");
        }
        Objects.requireNonNull(nextStep, "nextStep must not be null");
    }
}
