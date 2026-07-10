package com.bct.ngtpa.apiservice.application.dto.session.authflow;

public enum AuthFlowStatus {
    STARTED,
    OTP_SENT,
    OTP_VERIFIED,
    TRUST_SELECTION_READY,
    USERNAME_VERIFIED,
    COMPLETING,
    COMPLETED,
    CANCELLED,
    EXPIRED
}
