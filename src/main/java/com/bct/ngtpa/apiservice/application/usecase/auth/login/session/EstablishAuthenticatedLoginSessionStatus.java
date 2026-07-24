package com.bct.ngtpa.apiservice.application.usecase.auth.login.session;

public enum EstablishAuthenticatedLoginSessionStatus {
    ESTABLISHED,
    AUTH_FLOW_INVALID,
    SESSION_CREATION_FAILED,
    CLEANUP_FAILED
}
