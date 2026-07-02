package com.bct.ngtpa.apiservice.application.service.security.csrf;

public record OriginRefererValidationResult(
        OriginRefererValidationStatus status,
        boolean valid) {

    public static OriginRefererValidationResult success() {
        return new OriginRefererValidationResult(OriginRefererValidationStatus.VALID, true);
    }

    public static OriginRefererValidationResult failure(OriginRefererValidationStatus status) {
        return new OriginRefererValidationResult(status, false);
    }
}
