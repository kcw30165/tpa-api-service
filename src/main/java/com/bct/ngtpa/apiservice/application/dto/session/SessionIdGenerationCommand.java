package com.bct.ngtpa.apiservice.application.dto.session;

import java.util.Objects;
import java.util.regex.Pattern;

public record SessionIdGenerationCommand(String prefix, int entropyBytes) {
    public static final int MIN_ENTROPY_BYTES = 16;
    private static final Pattern SAFE_PREFIX = Pattern.compile("^[A-Za-z][A-Za-z0-9_-]{0,31}$");

    public SessionIdGenerationCommand {
        Objects.requireNonNull(prefix, "prefix must not be null");
        if (prefix.isBlank() || !SAFE_PREFIX.matcher(prefix).matches()) {
            throw new IllegalArgumentException("Session id prefix is invalid");
        }
        if (entropyBytes < MIN_ENTROPY_BYTES) {
            throw new IllegalArgumentException("Session id entropy must be at least 16 bytes");
        }
    }
}
