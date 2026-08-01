package com.bct.ngtpa.apiservice.application.dto.session.authflow;

import java.util.Objects;

/** Opaque application-owned concurrency metadata. */
public record SessionRevision(String value) {
    public SessionRevision {
        Objects.requireNonNull(value, "session revision must not be null");
        value = value.trim();
        if (value.isBlank()) {
            throw new IllegalArgumentException("session revision must not be blank");
        }
        if (!value.matches("[0-9a-f]{32}")) {
            throw new IllegalArgumentException("session revision format is invalid");
        }
    }

    @Override
    public String toString() {
        return "SessionRevision[value=<redacted>]";
    }
}
