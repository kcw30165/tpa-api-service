package com.bct.ngtpa.apiservice.adapter.out.security.login;

public final class LoginPreAuthContextProtectionException extends RuntimeException {
    public LoginPreAuthContextProtectionException(String message, Throwable cause) {
        super(message, cause);
    }

    public LoginPreAuthContextProtectionException(String message) {
        super(message);
    }
}
