package com.bct.ngtpa.apiservice.shared.config;

public class ConfigResolutionException extends RuntimeException {

    public ConfigResolutionException(String message) {
        super(message);
    }

    public ConfigResolutionException(String message, Throwable cause) {
        super(message, cause);
    }
}