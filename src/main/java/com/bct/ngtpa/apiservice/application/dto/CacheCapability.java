package com.bct.ngtpa.apiservice.application.dto;

/**
 * Supported cache capabilities for administrative cache operations.
 */
public enum CacheCapability {
    REFERENCE_DATE("reference-date");

    private final String keyPart;

    CacheCapability(String keyPart) {
        this.keyPart = keyPart;
    }

    public String keyPart() {
        return keyPart;
    }
}
