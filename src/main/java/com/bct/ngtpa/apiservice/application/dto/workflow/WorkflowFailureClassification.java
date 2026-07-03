package com.bct.ngtpa.apiservice.application.dto.workflow;

import java.util.Objects;

public record WorkflowFailureClassification(WorkflowFailureCategory category, WorkflowLogSeverity logSeverity) {
    public WorkflowFailureClassification {
        Objects.requireNonNull(category, "category must not be null");
        Objects.requireNonNull(logSeverity, "logSeverity must not be null");
    }

    public static WorkflowFailureClassification forCategory(WorkflowFailureCategory category) {
        Objects.requireNonNull(category, "category must not be null");
        return new WorkflowFailureClassification(category, switch (category) {
            case VALIDATION, BUSINESS, SECURITY, DOWNSTREAM_BUSINESS -> WorkflowLogSeverity.ERROR;
            case DOWNSTREAM_AVAILABILITY, SYSTEM -> WorkflowLogSeverity.FATAL;
        });
    }
}
