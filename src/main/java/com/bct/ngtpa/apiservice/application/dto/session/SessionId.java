package com.bct.ngtpa.apiservice.application.dto.session;

import java.util.Objects;
import java.util.regex.Pattern;

public record SessionId(String value) {
    private static final Pattern SAFE_SESSION_ID = Pattern.compile("^[A-Za-z0-9_-]+$");

    public SessionId {
        Objects.requireNonNull(value, "value must not be null");
        if (value.isBlank() || !SAFE_SESSION_ID.matcher(value).matches()) {
            throw new IllegalArgumentException("Session id value is invalid");
        }
    }
}
