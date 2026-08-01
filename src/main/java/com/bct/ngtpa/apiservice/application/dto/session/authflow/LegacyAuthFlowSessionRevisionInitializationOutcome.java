package com.bct.ngtpa.apiservice.application.dto.session.authflow;

public enum LegacyAuthFlowSessionRevisionInitializationOutcome {
    LEGACY_REVISION_INITIALIZED,
    SESSION_NOT_FOUND,
    REVISION_ALREADY_PRESENT,
    LEGACY_INITIALIZATION_CONFLICT,
    LOCK_OWNERSHIP_LOST,
    SCHEMA_NOT_ESTABLISHED,
    CORRUPT_STATE,
    SYSTEM_FAILURE
}
