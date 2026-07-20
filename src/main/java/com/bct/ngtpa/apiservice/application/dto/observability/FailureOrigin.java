package com.bct.ngtpa.apiservice.application.dto.observability;

public enum FailureOrigin {
    LOCAL_VALIDATION,
    LOCAL_BUSINESS,
    DOWNSTREAM_BUSINESS,
    DOWNSTREAM_CONTRACT,
    DOWNSTREAM_INVALID_RESPONSE,
    DOWNSTREAM_TRANSPORT,
    LOCAL_SYSTEM
}
