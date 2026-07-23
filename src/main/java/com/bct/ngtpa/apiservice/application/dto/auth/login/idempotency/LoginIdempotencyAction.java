package com.bct.ngtpa.apiservice.application.dto.auth.login.idempotency;
public enum LoginIdempotencyAction {
    CREDENTIALS("credentials");
    private final String keyNamespace;
    LoginIdempotencyAction(String value) { this.keyNamespace = value; }
    public String keyNamespace() { return keyNamespace; }
}
