package com.bct.ngtpa.apiservice.application.dto.auth.login.idempotency;

import java.util.regex.Pattern;

public record LoginIdempotencyKey(String value) {
    private static final Pattern SAFE = Pattern.compile("^[A-Za-z0-9_-]{8,128}$");

    public LoginIdempotencyKey {
        if (value == null || !SAFE.matcher(value).matches())
            throw new IllegalArgumentException("Invalid login idempotency key");
    }

    @Override
    public String toString() {
        return "LoginIdempotencyKey[redacted]";
    }
}
