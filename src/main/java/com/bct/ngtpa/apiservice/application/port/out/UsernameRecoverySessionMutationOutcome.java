package com.bct.ngtpa.apiservice.application.port.out;

/** Deterministic persistence outcomes for Username Recovery session operations. */
public enum UsernameRecoverySessionMutationOutcome {
    CREATED,
    LOADED,
    APPLIED,
    SESSION_NOT_FOUND,
    REVISION_CONFLICT,
    DELETED,
    ALREADY_ABSENT,
    SYSTEM_FAILURE
}
