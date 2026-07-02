package com.bct.ngtpa.apiservice.application.dto.security.csrf;

public record ValidateCsrfTokenResult(
        CsrfTokenValidationStatus status,
        boolean valid) {

    public static ValidateCsrfTokenResult success() {
        return new ValidateCsrfTokenResult(CsrfTokenValidationStatus.VALID, true);
    }

    public static ValidateCsrfTokenResult failure(CsrfTokenValidationStatus status) {
        return new ValidateCsrfTokenResult(status, false);
    }
}
