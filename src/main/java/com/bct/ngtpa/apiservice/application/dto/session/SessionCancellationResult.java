package com.bct.ngtpa.apiservice.application.dto.session;

import java.util.Objects;

public record SessionCancellationResult(SessionCancellationStatus status) {
    public SessionCancellationResult {
        Objects.requireNonNull(status, "status must not be null");
    }

    public boolean cancelled() {
        return status == SessionCancellationStatus.CANCELLED
                || status == SessionCancellationStatus.ALREADY_CANCELLED;
    }
}
