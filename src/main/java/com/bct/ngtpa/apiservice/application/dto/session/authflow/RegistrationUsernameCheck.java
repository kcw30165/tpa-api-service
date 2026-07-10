package com.bct.ngtpa.apiservice.application.dto.session.authflow;

import com.bct.ngtpa.apiservice.application.dto.auth.registration.RegistrationSelectedTrust;
import java.time.Instant;

/**
 * Session-state contract for a successfully verified registration username.
 */
public record RegistrationUsernameCheck(
        RegistrationSelectedTrust selectedTrust,
        String username,
        Instant checkedAt,
        Instant expiresAt) {
    @Override
    public String toString() {
        return "RegistrationUsernameCheck[selectedTrust=" + selectedTrust
                + ", username=<redacted>, checkedAt=" + checkedAt
                + ", expiresAt=" + expiresAt + "]";
    }
}
