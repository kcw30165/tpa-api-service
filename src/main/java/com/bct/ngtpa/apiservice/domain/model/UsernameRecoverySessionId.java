package com.bct.ngtpa.apiservice.domain.model;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Opaque protected Username Recovery session identifier value object.
 *
 * <p>Design references:
 *
 * <ul>
 *   <li>{@code docs/design/6-username-recovery/username-recovery-session-redis-design.md}
 *       section 3 (session identity retained as a session field);</li>
 *   <li>{@code docs/design/6-username-recovery/username-recovery-solution-design.md}
 *       section 7 (minimal protected session data; session identifier is never a raw transport
 *       header).</li>
 * </ul>
 *
 * <p>Invariants: the value is non-null, nonblank, and restricted to safe opaque characters.
 * {@link #toString()} never discloses the value.
 */
public record UsernameRecoverySessionId(String value) {

    private static final Pattern SAFE_ID = Pattern.compile("^[A-Za-z0-9_-]+$");

    public UsernameRecoverySessionId {
        Objects.requireNonNull(value, "session id must not be null");
        if (value.isBlank() || !SAFE_ID.matcher(value).matches()) {
            throw new IllegalArgumentException("session id must be a safe nonblank opaque value");
        }
    }

    @Override
    public String toString() {
        return "UsernameRecoverySessionId[value=<redacted>]";
    }
}
