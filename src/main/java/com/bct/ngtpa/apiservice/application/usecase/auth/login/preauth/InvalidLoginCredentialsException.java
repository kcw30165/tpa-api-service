package com.bct.ngtpa.apiservice.application.usecase.auth.login.preauth;

public final class InvalidLoginCredentialsException extends RuntimeException {
    public InvalidLoginCredentialsException(String message) {
        super(message);
    }
}
