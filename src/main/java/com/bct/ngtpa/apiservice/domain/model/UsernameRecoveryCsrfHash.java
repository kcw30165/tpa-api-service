package com.bct.ngtpa.apiservice.domain.model;

import java.util.Objects;

/**
 * Protected Username Recovery CSRF hash value object.
 *
 * <p>Design references:
 *
 * <ul>
 *   <li>{@code docs/design/6-username-recovery/username-recovery-session-redis-design.md}
 *       section 3 (CSRF hash retained as a session field);</li>
 *   <li>{@code docs/design/6-username-recovery/username-recovery-solution-design.md}
 *       section 7 (CSRF hash retained as minimal protected data).</li>
 * </ul>
 *
 * <p>Only the hash/protected representation is retained; the raw CSRF token is never stored.
 * Hashing and token generation belong to a later task, so this value object validates only that the
 * hash is present and nonblank. {@link #toString()} never discloses the hash value.
 */
public record UsernameRecoveryCsrfHash(String value) {

    public UsernameRecoveryCsrfHash {
        Objects.requireNonNull(value, "csrf hash must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException("csrf hash must not be blank");
        }
    }

    @Override
    public String toString() {
        return "UsernameRecoveryCsrfHash[value=<redacted>]";
    }
}
