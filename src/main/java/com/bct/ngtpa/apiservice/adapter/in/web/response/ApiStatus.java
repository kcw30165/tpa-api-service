package com.bct.ngtpa.apiservice.adapter.in.web.response;

/**
 * BFF-owned high-level operation status values used by the generic API
 * envelope.
 */

public enum ApiStatus {
    SUCCESS,
    UPDATED,
    SUBMITTED,
    NO_CHANGE,
    PENDING_APPROVAL,
    // Java-Side Local Failures
    VALIDATION_FAILED, // Java input validation failed (e.g., Spring @Valid, bad formats)
    BUSINESS_REJECTED, // Java local business logic rejected it (e.g., user is locked out locally)
    SYSTEM_ERROR, // Java crashed (NullPointer, local DB down)

    // Downstream / APIM Failures
    DOWNSTREAM_REJECTED, // APIM successfully processed but returned a payload business error
    DOWNSTREAM_ERROR // APIM threw a 500, timed out, or returned an invalid/corrupt envelope
}
