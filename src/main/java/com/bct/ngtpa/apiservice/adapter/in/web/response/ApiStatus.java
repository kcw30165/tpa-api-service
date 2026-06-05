package com.bct.ngtpa.apiservice.adapter.in.web.response;

/**
 * BFF-owned high-level operation status values used by the generic API envelope.
 */

public enum ApiStatus {
    SUCCESS,
    UPDATED,
    SUBMITTED,
    NO_CHANGE,
    PENDING_APPROVAL,
    VALIDATION_FAILED,
    BUSINESS_REJECTED,
    SYSTEM_ERROR
}

