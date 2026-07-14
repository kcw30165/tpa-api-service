package com.bct.ngtpa.apiservice.application.dto.session.authflow;

/**
 * Safe, non-PII reasons for cancelling a pre-authentication workflow session.
 */
public enum AuthFlowSessionCancellationReason {
    USER_CANCELLED,
    REPLACED_BY_NEW_FLOW,
    REGISTRATION_MEMBER_NOT_FOUND,
    SECURITY_REJECTION,
    ADMINISTRATIVE
}
