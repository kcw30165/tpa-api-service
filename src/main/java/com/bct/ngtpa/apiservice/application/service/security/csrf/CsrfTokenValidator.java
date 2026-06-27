package com.bct.ngtpa.apiservice.application.service.security.csrf;

import com.bct.ngtpa.apiservice.application.dto.security.csrf.CsrfTokenValidationStatus;
import com.bct.ngtpa.apiservice.application.dto.security.csrf.ValidateCsrfTokenCommand;
import com.bct.ngtpa.apiservice.application.dto.security.csrf.ValidateCsrfTokenResult;

import java.time.Instant;
import java.util.Objects;

public class CsrfTokenValidator {

    private final CsrfTokenHasher hasher;

    public CsrfTokenValidator(CsrfTokenHasher hasher) {
        this.hasher = Objects.requireNonNull(hasher, "hasher must not be null");
    }

    public ValidateCsrfTokenResult validate(ValidateCsrfTokenCommand command) {
        if (command == null || command.now() == null || command.expiresAt() == null) {
            return ValidateCsrfTokenResult.failure(CsrfTokenValidationStatus.INVALID_INPUT);
        }
        if (isBlank(command.submittedToken()) || isBlank(command.storedTokenHash())) {
            return ValidateCsrfTokenResult.failure(CsrfTokenValidationStatus.MISSING);
        }
        Instant now = command.now();
        if (!command.expiresAt().isAfter(now)) {
            return ValidateCsrfTokenResult.failure(CsrfTokenValidationStatus.EXPIRED);
        }
        if (!hasher.matches(command.submittedToken(), command.storedTokenHash())) {
            return ValidateCsrfTokenResult.failure(CsrfTokenValidationStatus.MISMATCH);
        }
        return ValidateCsrfTokenResult.success();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
