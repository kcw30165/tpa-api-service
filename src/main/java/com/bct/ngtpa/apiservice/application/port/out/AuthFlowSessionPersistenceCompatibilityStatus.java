package com.bct.ngtpa.apiservice.application.port.out;

public enum AuthFlowSessionPersistenceCompatibilityStatus {
    COMPATIBLE,
    INITIALIZED,
    CONFIGURATION_VERSION_UNSUPPORTED,
    EXISTING_VERSION_MISMATCH,
    MARKER_CORRUPT,
    STORAGE_UNAVAILABLE,
    INITIALIZATION_CONFLICT,
    SYSTEM_FAILURE
}
