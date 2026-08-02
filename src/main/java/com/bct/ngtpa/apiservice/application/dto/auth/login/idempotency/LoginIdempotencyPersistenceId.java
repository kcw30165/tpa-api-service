package com.bct.ngtpa.apiservice.application.dto.auth.login.idempotency;

import java.util.regex.Pattern;

public record LoginIdempotencyPersistenceId(String value) {
    private static final Pattern SAFE = Pattern.compile("^[A-Za-z0-9_-]{43}$");

    public LoginIdempotencyPersistenceId {
        if (value == null || !SAFE.matcher(value).matches())
            throw new IllegalArgumentException("Invalid Login idempotency persistence identity");
    }

    @Override
    public String toString() {
        return "LoginIdempotencyPersistenceId[redacted]";
    }
}
