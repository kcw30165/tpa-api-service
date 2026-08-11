package com.bct.ngtpa.apiservice.application.port.out;

/** Deterministic persistence outcomes for Username Recovery workflow-state operations. */
public enum UsernameRecoveryWorkflowStateMutationOutcome {
    CREATED,
    LOADED,
    APPLIED,
    STATE_NOT_FOUND,
    REVISION_CONFLICT,
    DELETED,
    ALREADY_ABSENT,
    SYSTEM_FAILURE
}
