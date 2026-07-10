package com.bct.ngtpa.apiservice.application.dto.session.authflow;

import java.time.Instant;
import java.util.Objects;

public record UsernameCheckState(SelectedTrust selectedTrust, String username, Instant checkedAt, Instant expiresAt) {
    public UsernameCheckState {
        Objects.requireNonNull(selectedTrust, "selectedTrust must not be null");
        Objects.requireNonNull(username, "username must not be null");
        if (username.isBlank()) {
            throw new IllegalArgumentException("username must not be blank");
        }
        Objects.requireNonNull(checkedAt, "checkedAt must not be null");
    }

    @Override
    public String toString() {
        return "UsernameCheckState[selectedTrust=" + selectedTrust + ", username=<redacted>, checkedAt=" + checkedAt + ", expiresAt=" + expiresAt + "]";
    }
}
