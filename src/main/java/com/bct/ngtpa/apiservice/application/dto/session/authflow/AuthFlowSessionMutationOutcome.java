package com.bct.ngtpa.apiservice.application.dto.session.authflow;

public enum AuthFlowSessionMutationOutcome {
    APPLIED,
    SESSION_NOT_FOUND,
    SESSION_REVISION_MISSING,
    SESSION_REVISION_CHANGED,
    EXPECTED_STATE_CHANGED,
    INVALID_TRANSITION,
    CORRUPT_STATE,
    SCHEMA_NOT_ESTABLISHED,
    SYSTEM_FAILURE
}
