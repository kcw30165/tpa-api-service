package com.bct.ngtpa.apiservice.application.dto.workflow;

public enum WorkflowFailureCategory {
    VALIDATION,
    BUSINESS,
    SECURITY,
    DOWNSTREAM_BUSINESS,
    DOWNSTREAM_AVAILABILITY,
    SYSTEM
}
