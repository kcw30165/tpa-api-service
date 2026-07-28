package com.bct.ngtpa.apiservice.application.usecase.session.authflow;

import java.util.Objects;

/**
 * Central token/hash comparison used by pre-auth and authenticated sessions.
 * This service does not load sessions and does not own flow-specific HTTP/error mappings.
 */
public final class CsrfTokenValidationService {
    private final CsrfTokenHasher csrfTokenHasher;

    public CsrfTokenValidationService(CsrfTokenHasher csrfTokenHasher) {
        this.csrfTokenHasher = Objects.requireNonNull(
                csrfTokenHasher, "csrfTokenHasher must not be null");
    }

    public CsrfTokenValidationResult validate(String submittedToken, String storedTokenHash) {
        if (submittedToken == null || submittedToken.isBlank()) {
            return result(CsrfTokenValidationStatus.TOKEN_MISSING);
        }
        if (storedTokenHash == null || storedTokenHash.isBlank()) {
            return result(CsrfTokenValidationStatus.TOKEN_HASH_MISSING);
        }
        if (!csrfTokenHasher.matches(submittedToken, storedTokenHash)) {
            return result(CsrfTokenValidationStatus.TOKEN_MISMATCH);
        }
        return result(CsrfTokenValidationStatus.VALID);
    }

    private static CsrfTokenValidationResult result(CsrfTokenValidationStatus status) {
        return new CsrfTokenValidationResult(status);
    }
}
