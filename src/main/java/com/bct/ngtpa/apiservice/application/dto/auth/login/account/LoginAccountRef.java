package com.bct.ngtpa.apiservice.application.dto.auth.login.account;

import java.util.regex.Pattern;

public record LoginAccountRef(String value) {
    private static final Pattern SAFE = Pattern.compile("^[A-Za-z][A-Za-z0-9_-]{19,127}$");

    public LoginAccountRef {
        if (value == null || !SAFE.matcher(value).matches())
            throw new IllegalArgumentException("Invalid opaque account reference");
    }

    @Override
    public String toString() {
        return "LoginAccountRef[redacted]";
    }
}
