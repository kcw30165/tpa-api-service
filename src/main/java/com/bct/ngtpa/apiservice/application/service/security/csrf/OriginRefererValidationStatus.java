package com.bct.ngtpa.apiservice.application.service.security.csrf;

public enum OriginRefererValidationStatus {
    VALID,
    MISSING,
    ORIGIN_NOT_ALLOWED,
    REFERER_NOT_ALLOWED,
    MALFORMED
}
