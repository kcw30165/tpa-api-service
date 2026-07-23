package com.bct.ngtpa.apiservice.application.dto.auth.login;

public enum CredentialAuthenticationStatus {
    AUTHENTICATED,
    INCORRECT_PASSWORD,
    INCORRECT_USERNAME,
    INVALID_ACCOUNT,
    ACCOUNT_LOCKED,
    UNAVAILABLE
}
