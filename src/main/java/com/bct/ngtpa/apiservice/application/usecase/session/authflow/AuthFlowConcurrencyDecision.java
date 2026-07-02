package com.bct.ngtpa.apiservice.application.usecase.session.authflow;

public enum AuthFlowConcurrencyDecision {
    CREATED_NEW,
    REPLACED_EXISTING,
    CREATED_NEW_EXISTING_MISSING,
    CREATED_NEW_EXISTING_ALREADY_TERMINAL
}
