package com.bct.ngtpa.apiservice.application.dto.security.csrf;

public enum CsrfTokenValidationStatus {
    VALID,
    MISSING,
    MISMATCH,
    EXPIRED,
    INVALID_INPUT
}
