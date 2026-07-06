package com.bct.ngtpa.apiservice.application.dto.workflow;

import java.util.Objects;

public final class WorkflowSeverityMapper {
    private WorkflowSeverityMapper() {
    }

    public static WorkflowLogSeverity severityFor(WorkflowFailureCategory category) {
        Objects.requireNonNull(category, "category must not be null");
        return WorkflowFailureClassification.forCategory(category).logSeverity();
    }
}
